package com.example.backend.websocket;

import com.example.backend.utils.JwtUtils;
import com.example.backend.utils.AccountStatusUtils;
import com.example.backend.entity.SysUser;
import com.example.backend.service.SysUserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器：解析并校验 Token
 */
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private SysUserService sysUserService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
            token = httpServletRequest.getParameter("token");
            if (!StringUtils.hasText(token)) {
                String header = httpServletRequest.getHeader("Authorization");
                if (StringUtils.hasText(header)) {
                    token = header.startsWith("Bearer ") ? header.substring(7) : header;
                }
            }
        }

        if (!StringUtils.hasText(token)) {
            return false;
        }

        try {
            Claims claims = jwtUtils.getClaimsByToken(token);
            String userId = claims.getSubject();
            if (!StringUtils.hasText(userId)) {
                return false;
            }
            Long uid = Long.parseLong(userId);
            SysUser user = sysUserService.getById(uid);
            if (AccountStatusUtils.getBlockedMessage(user) != null) {
                return false;
            }
            attributes.put("userId", uid);
            attributes.put("role", claims.get("role", String.class));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手后无需处理
    }
}
