package com.example.backend.integration;

import com.example.backend.dto.ChatWsPushDTO;
import com.example.backend.dto.ChatWsSendDTO;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.ApplicantInfoMapper;
import com.example.backend.mapper.MerchantInfoMapper;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.service.ChatService;
import com.example.backend.service.SysUserService;
import com.example.backend.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 聊天 WebSocket 集成测试。
 * 本轮先覆盖握手鉴权、心跳与在线推送主干。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatWebSocketIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    @MockBean
    private SysUserService sysUserService;

    @MockBean
    private SysUserMapper sysUserMapper;

    @MockBean
    private MerchantInfoMapper merchantInfoMapper;

    @MockBean
    private ApplicantInfoMapper applicantInfoMapper;

    private final List<WebSocketSession> openedSessions = new CopyOnWriteArrayList<>();

    @AfterEach
    void closeSessions() {
        for (WebSocketSession session : openedSessions) {
            try {
                if (session != null && session.isOpen()) {
                    session.close();
                }
            } catch (Exception ignored) {
            }
        }
        openedSessions.clear();
    }

    @Test
    void shouldRejectHandshakeWhenTokenMissing() {
        StandardWebSocketClient client = new StandardWebSocketClient();

        assertThatThrownBy(() -> client.execute(new QueueingHandler(), wsHeaders(), wsUri("")).get(5, TimeUnit.SECONDS))
                .as("缺少 token 时 WebSocket 握手应失败")
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldRespondPongWhenClientSendsPing() throws Exception {
        long userId = 1001L;
        mockActiveUser(userId, "ADMIN", "测试管理员");

        QueueingHandler handler = new QueueingHandler();
        WebSocketSession session = connect(userId, "ADMIN", handler);

        session.sendMessage(new TextMessage("PING"));

        assertThat(handler.pollText(Duration.ofSeconds(5))).isEqualTo("PONG");
    }

    @Test
    void shouldPushMessageToOnlineReceiver() throws Exception {
        long senderId = 1001L;
        long receiverId = 2002L;
        mockActiveUser(senderId, "ADMIN", "发送者");
        mockActiveUser(receiverId, "ADMIN", "接收者");

        QueueingHandler receiverHandler = new QueueingHandler();
        QueueingHandler senderHandler = new QueueingHandler();
        WebSocketSession receiverSession = connect(receiverId, "ADMIN", receiverHandler);
        WebSocketSession senderSession = connect(senderId, "ADMIN", senderHandler);

        ChatWsSendDTO payload = new ChatWsSendDTO();
        payload.setToUserId(receiverId);
        payload.setContent("WebSocket 在线消息");

        senderSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));

        String rawMessage = receiverHandler.pollText(Duration.ofSeconds(5));
        ChatWsPushDTO pushDTO = objectMapper.readValue(rawMessage, ChatWsPushDTO.class);

        assertThat(pushDTO.getFromUserId()).isEqualTo(senderId);
        assertThat(pushDTO.getToUserId()).isEqualTo(receiverId);
        assertThat(pushDTO.getContent()).isEqualTo("WebSocket 在线消息");
        assertThat(pushDTO.getSenderName()).isEqualTo("发送者");

        verify(chatService).saveMessage(senderId, receiverId, "WebSocket 在线消息");
    }

    private WebSocketSession connect(long userId, String role, QueueingHandler handler) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler, wsHeaders(), wsUri(jwtUtils.generateToken(userId, role)))
                .get(5, TimeUnit.SECONDS);
        openedSessions.add(session);
        return session;
    }

    private WebSocketHttpHeaders wsHeaders() {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.ACCEPT, "application/json");
        return headers;
    }

    private URI wsUri(String token) {
        String query = token == null || token.isBlank() ? "" : "?token=" + token;
        return URI.create("ws://127.0.0.1:" + port + "/ws" + query);
    }

    private void mockActiveUser(long userId, String role, String nickname) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setRole(role);
        user.setUsername("user" + userId);
        user.setNickname(nickname);
        user.setStatus(1);
        user.setBanStatus(0);
        when(sysUserService.getById(userId)).thenReturn(user);
        when(sysUserMapper.selectById(userId)).thenReturn(user);
    }

    /**
     * 收集服务端推送文本，便于在测试里做同步断言。
     */
    private static class QueueingHandler extends TextWebSocketHandler {

        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.offer(message.getPayload());
        }

        String pollText(Duration timeout) throws InterruptedException {
            String payload = messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(payload)
                    .as("应在限定时间内收到服务端推送")
                    .isNotNull();
            return payload;
        }
    }
}
