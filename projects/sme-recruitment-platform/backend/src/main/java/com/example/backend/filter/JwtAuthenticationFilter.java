/*
 * 文件速览：
 * 1. 文件职责：解析 JWT、恢复登录态，并在账号受限时切换为“只读治理提醒模式”。
 * 2. 对外入口：Spring Security 过滤链中的 JwtAuthenticationFilter。
 * 3. 关键结构：Token 解析、账号状态校验、受限治理接口白名单、401/403 JSON 返回。
 * 4. 阅读建议：先看 doFilterInternal，再看 isRestrictedRequestAllowed 与 writeRestrictedResponse。
 */
package com.example.backend.filter;

import com.example.backend.utils.JwtUtils;
import com.example.backend.entity.SysUser;
import com.example.backend.service.SysUserService;
import com.example.backend.utils.AccountStatusUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private SysUserService sysUserService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 预检请求直接放行
        if ("OPTIONS".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        
        if (StringUtils.hasText(token)) {
            // Remove "Bearer " prefix if present
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            
            try {
                Claims claims = jwtUtils.getClaimsByToken(token);
                String userId = claims.getSubject();
                String role = claims.get("role", String.class);
                
                if (userId != null) {
                    Long uid;
                    try {
                        uid = Long.parseLong(userId);
                    } catch (NumberFormatException e) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    SysUser user = sysUserService.getById(uid);
                    String blockedMsg = AccountStatusUtils.getBlockedMessage(user);
                    if (blockedMsg != null) {
                        if (!AccountStatusUtils.allowRestrictedNoticeMode(user)) {
                            writeUnauthorizedResponse(response, blockedMsg);
                            return;
                        }
                        if (!isAuthPath(request) && !isRestrictedRequestAllowed(request)) {
                            writeRestrictedResponse(response, blockedMsg);
                            return;
                        }
                        request.setAttribute("restrictedMode", true);
                        request.setAttribute("restrictedReason", blockedMsg);
                    }
                    List<SimpleGrantedAuthority> authorities = role != null ? 
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)) : 
                            Collections.emptyList();

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // Token 无效，但不阻断，交给 SecurityConfig 处理
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 常规接口优先走 Authorization；受控文件预览允许通过 query token 恢复登录态，
     * 以兼容 img/iframe/window.open 这类无法附带自定义请求头的浏览器能力。
     */
    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (StringUtils.hasText(token)) {
            return token;
        }
        String requestUri = request.getRequestURI();
        if (requestUri != null && requestUri.startsWith("/uploads/")) {
            return request.getParameter("token");
        }
        return null;
    }

    /**
     * 受限账号仍可访问的治理接口：
     * 1. 平台提醒列表 / 详情只读访问
     * 2. 封禁通知申诉动作入口（具体是否允许由业务层二次校验）
     */
    private boolean isRestrictedRequestAllowed(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        if ("GET".equalsIgnoreCase(method) && path.startsWith("/governance/notices/my")) {
            return true;
        }
        return "POST".equalsIgnoreCase(method) && path.matches("^/governance/notices/\\d+/actions$");
    }

    /**
     * 认证相关接口在受限态下也保持放行，避免退出登录失败。
     */
    private boolean isAuthPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/auth/");
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String blockedMsg) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        String safeMsg = blockedMsg.replace("\"", "\\\"");
        response.getWriter().write("{\"code\": 401, \"msg\": \"" + safeMsg + "\", \"data\": null}");
    }

    /**
     * 受限账号访问非白名单接口时返回 403，前端可据此切到只读提醒模式。
     */
    private void writeRestrictedResponse(HttpServletResponse response, String blockedMsg) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        String safeMsg = blockedMsg.replace("\"", "\\\"");
        response.getWriter().write(
                "{\"code\": 403, \"msg\": \"" + safeMsg + "\", " +
                        "\"data\": {\"restrictedMode\": true, \"restrictedReason\": \"" + safeMsg + "\"}}"
        );
    }
}
