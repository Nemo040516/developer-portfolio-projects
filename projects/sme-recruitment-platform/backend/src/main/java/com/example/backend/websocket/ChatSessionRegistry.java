package com.example.backend.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 会话注册表
 * 统一管理在线用户会话，用于后端主动推送消息
 */
@Component
public class ChatSessionRegistry {

    private final Map<Long, Set<WebSocketSession>> sessionPool = new ConcurrentHashMap<>();

    /**
     * 注册会话
     */
    public void addSession(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        sessionPool.computeIfAbsent(userId, key -> new CopyOnWriteArraySet<>()).add(session);
    }

    /**
     * 移除会话
     */
    public void removeSession(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        Set<WebSocketSession> sessions = sessionPool.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionPool.remove(userId);
            }
        }
    }

    /**
     * 向指定用户推送消息
     * @return 是否存在可用连接（不保证客户端一定收到）
     */
    public boolean sendToUser(Long userId, String payload) throws IOException {
        if (userId == null || payload == null) {
            return false;
        }
        Set<WebSocketSession> sessions = sessionPool.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                session.sendMessage(message);
            }
        }
        return true;
    }
}
