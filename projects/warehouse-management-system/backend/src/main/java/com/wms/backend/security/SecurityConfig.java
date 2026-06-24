/**
 * @file 速览索引
 * @summary Spring Security 权限总入口，负责接口放行规则、角色访问矩阵与鉴权过滤链配置。
 * @core 1. 配置登录与公开接口放行
 * @core 2. 约束管理员、采购员、仓库员的接口访问范围
 * @core 3. 挂接 JWT 或认证过滤逻辑
 * @entry 先看：securityFilterChain / filterChain、requestMatchers 配置段
 * @deps 关键依赖：AuthService、MenuService、JWT 相关组件、application.yml
 * @risk 高风险修改点：接口权限矩阵、白名单配置、菜单权限与接口权限一致性
 * @link 相关文件：后端/src/main/java/com/wms/backend/auth/service/MenuService.java、后端/src/test/java/com/wms/backend/M1SecurityIntegrationTest.java
 */
package com.wms.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.backend.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/ping", "/error").permitAll()
                        // 离线包场景：前端静态资源由后端 8080 端口直出，放行所有非 /api 的 GET 请求。
                        .requestMatchers(request -> HttpMethod.GET.matches(request.getMethod()) && !request.getRequestURI().startsWith("/api/")).permitAll()
                        .requestMatchers("/api/users/**", "/api/roles/**").hasRole("ADMIN")
                        // 采购员在 M6 生成建议时需要读取仓库下拉选项，仅开放该只读入口。
                        .requestMatchers(HttpMethod.GET, "/api/warehouses/options").hasAnyRole("ADMIN", "WAREHOUSE", "PURCHASER")
                        .requestMatchers("/api/warehouses/**", "/api/locations/**").hasAnyRole("ADMIN", "WAREHOUSE")
                        .requestMatchers("/api/inventory/alerts/**").hasAnyRole("ADMIN", "WAREHOUSE", "PURCHASER")
                        .requestMatchers("/api/inventory/**").hasAnyRole("ADMIN", "WAREHOUSE")
                        .requestMatchers("/api/inbounds/**", "/api/putaways/**", "/api/outbounds/**", "/api/stocktakes/**").hasAnyRole("ADMIN", "WAREHOUSE")
                        .requestMatchers(HttpMethod.GET, "/api/replenishments/**").hasAnyRole("ADMIN", "WAREHOUSE", "PURCHASER")
                        .requestMatchers("/api/replenishments/**").hasAnyRole("ADMIN", "PURCHASER")
                        // SKU 读写分离：
                        // - 采购员仅保留只读（GET）能力，用于浏览商品基础信息；
                        // - 写操作（POST/PUT）仅允许管理员与仓库员，避免越权维护主数据。
                        .requestMatchers(HttpMethod.GET, "/api/skus/**").hasAnyRole("ADMIN", "WAREHOUSE", "PURCHASER")
                        .requestMatchers("/api/skus/**").hasAnyRole("ADMIN", "WAREHOUSE")
                        .requestMatchers("/api/suppliers/**").hasAnyRole("ADMIN", "PURCHASER")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint((request, response, authException) -> {
                            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ApiResponse.fail(4010, "未登录或登录已过期"));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            writeJson(response, HttpServletResponse.SC_FORBIDDEN, ApiResponse.fail(4030, "无权限访问"));
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeJson(HttpServletResponse response, int status, ApiResponse<Void> body) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
