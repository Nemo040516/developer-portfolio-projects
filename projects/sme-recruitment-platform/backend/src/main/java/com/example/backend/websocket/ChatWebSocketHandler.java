/*
 * 文件速览：
 * 1. 文件职责：处理聊天 WebSocket 连接、心跳、消息落库与在线推送。
 * 2. 对外入口：WebSocketConfig 注册的 /ws/chat 处理器。
 * 3. 关键结构：连接建连/断连、PING/PONG、ChatWsSendDTO 解析、发送者展示名补齐。
 * 4. 阅读建议：先看 handleTextMessage，再看 resolveDisplayName。
 */
package com.example.backend.websocket;

import com.example.backend.dto.ChatWsPushDTO;
import com.example.backend.dto.ChatWsSendDTO;
import com.example.backend.service.ChatService;
import com.example.backend.support.ChatPresentationSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * WebSocket 消息处理器
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatSessionRegistry sessionRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatPresentationSupport chatPresentationSupport;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = getUserId(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        sessionRegistry.addSession(userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long fromUserId = getUserId(session);
        if (fromUserId == null) {
            return;
        }

        // 心跳检测：客户端发送 PING，服务端返回 PONG
        String rawPayload = message.getPayload();
        if (StringUtils.hasText(rawPayload) && "PING".equalsIgnoreCase(rawPayload.trim())) {
            session.sendMessage(new TextMessage("PONG"));
            return;
        }

        ChatWsSendDTO payload;
        try {
            payload = objectMapper.readValue(message.getPayload(), ChatWsSendDTO.class);
        } catch (Exception ex) {
            // 非 JSON 消息直接忽略，避免中断连接
            return;
        }
        if (payload == null || payload.getToUserId() == null || !StringUtils.hasText(payload.getContent())) {
            return;
        }
        String normalizedContent = payload.getContent().trim();
        if (!StringUtils.hasText(normalizedContent)) {
            return;
        }

        // 落库
        chatService.saveMessage(fromUserId, payload.getToUserId(), normalizedContent);

        // 构造推送给接收方的消息体
        ChatWsPushDTO pushDTO = new ChatWsPushDTO();
        pushDTO.setFromUserId(fromUserId);
        pushDTO.setToUserId(payload.getToUserId());
        pushDTO.setContent(normalizedContent);
        pushDTO.setCreateTime(LocalDateTime.now().format(TIME_FORMATTER));

        // 补充发送者展示信息
        chatPresentationSupport.fillSenderProfile(pushDTO, fromUserId);

        // 推送给接收方（若在线）
        sessionRegistry.sendToUser(payload.getToUserId(), objectMapper.writeValueAsString(pushDTO));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = getUserId(session);
        if (userId == null) {
            return;
        }
        sessionRegistry.removeSession(userId, session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        session.close(CloseStatus.SERVER_ERROR);
    }

    private Long getUserId(WebSocketSession session) {
        Object value = session.getAttributes().get("userId");
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof String str && StringUtils.hasText(str)) {
            return Long.parseLong(str);
        }
        return null;
    }
}
