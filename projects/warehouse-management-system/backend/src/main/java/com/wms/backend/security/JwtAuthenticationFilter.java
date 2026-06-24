/**
 * @file 速览索引
 * @summary JWT 鉴权过滤器，负责从请求头解析令牌并写入 Spring Security 上下文。
 * @core 1. 读取 Authorization: Bearer token
 * @core 2. 解析 claims 中 subject 与 role
 * @core 3. 将用户身份转换为 ROLE_* 权限并写入 SecurityContext
 * @core 4. 解析失败时清空上下文，避免脏认证残留
 * @entry 先看：doFilterInternal
 * @deps 关键依赖：JwtTokenService.parse、SecurityContextHolder、SecurityConfig.addFilterBefore
 * @state 关键规则：角色来源 claims(\"role\")，权限前缀固定为 ROLE_
 * @risk 高风险修改点：token 前缀判断、role claim 键名、异常吞掉后的上下文处理
 * @link 相关文件：后端/src/main/java/com/wms/backend/security/SecurityConfig.java、后端/src/main/java/com/wms/backend/security/JwtTokenService.java
 */
package com.wms.backend.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtTokenService.parse(token);
                String username = claims.getSubject();
                String roleCode = claims.get("role", String.class);
                UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + roleCode))
                        );
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
