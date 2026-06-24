/*
 * 文件速览：
 * 1. 文件职责：定义 Spring Security 主鉴权链路、匿名放行范围与跨域策略。
 * 2. 对外入口：SecurityFilterChain、PasswordEncoder、CorsConfigurationSource。
 * 3. 关键结构：JWT 过滤器接入、401 JSON 返回、/uploads/** 受控放行。
 * 4. 阅读建议：先看 filterChain，再看 corsConfigurationSource。
 */
package com.example.backend.config;

import com.example.backend.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. 开启 CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 2. 禁用 CSRF
            .csrf(AbstractHttpConfigurer::disable)
            // 3. 配置权限
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/jobs/public/**").permitAll() // 允许未登录访问公开职位接口
                .requestMatchers("/jobs/search").permitAll() // 允许未登录访问搜索接口
                .requestMatchers("/merchant/detail/**").permitAll() // 允许查看商家基础资料
                .requestMatchers("/category/**", "/categories/**").permitAll() // 允许未登录访问分类接口
                .requestMatchers("/user/me").permitAll() // 允许未登录访问用户信息接口（前端需自行判断是否登录）
                .requestMatchers("/uploads/**").permitAll() // 文件访问统一交由 UploadAccessController 按目录与身份做鉴权
                .requestMatchers("/ws", "/ws/**").permitAll() // WebSocket 握手放行，认证由握手拦截器处理
                .anyRequest().authenticated()
            )
            // 4. 【关键修改】配置异常处理：未登录或 Token 无效时返回 401 而不是 403
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"code\": 401, \"msg\": \"凭证已失效，请重新登录\", \"data\": null}");
                })
            )
            // 5. 添加 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOriginPattern("*");
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
