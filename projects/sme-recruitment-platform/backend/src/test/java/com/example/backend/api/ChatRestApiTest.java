/**
 * 文件速览：
 * 1. 文件职责：覆盖聊天 REST 控制器的登录态、参数校验、分页兼容与会话上下文更新行为。
 * 2. 关键升级：本次补充 sessionId 优先链路的 API 测试。
 * 3. 关键断言：消息分页、标记已读、岗位切换均需覆盖 peerId 与 sessionId 两种入口。
 * 4. 阅读建议：先看分页测试，再看已读与岗位切换的兼容性测试。
 */
package com.example.backend.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.dto.ChatSessionJobUpdateDTO;
import com.example.backend.entity.ChatMessage;
import com.example.backend.entity.ChatSession;
import com.example.backend.service.ChatService;
import com.example.backend.support.ApiTestBase;
import com.example.backend.vo.ChatMessageVO;
import com.example.backend.vo.ChatSessionVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 聊天 REST 接口测试。
 * 本轮先覆盖登录态、分页兼容、参数校验与核心成功流。
 */
class ChatRestApiTest extends ApiTestBase {

    @MockBean
    private ChatService chatService;

    @Test
    void shouldRequireLoginWhenListingSessions() throws Exception {
        mockMvc.perform(get("/chat/session/list"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldReturnSessionListForAuthenticatedUser() throws Exception {
        ChatSessionVO session = new ChatSessionVO();
        session.setId(2L);
        session.setSessionId(22L);
        session.setPeerId(2L);
        session.setPeerName("测试商家");
        session.setJobTitle("Java 后端");
        session.setLastMessage("你好");
        session.setUnreadCount(3);
        session.setLastTime(LocalDateTime.of(2026, 3, 3, 10, 0, 0));
        when(chatService.getSessionList(1L)).thenReturn(List.of(session));

        mockMvc.perform(get("/chat/session/list")
                        .with(authorizedAs(1L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].peerId").value(2))
                .andExpect(jsonPath("$.data[0].peerName").value("测试商家"))
                .andExpect(jsonPath("$.data[0].jobTitle").value("Java 后端"))
                .andExpect(jsonPath("$.data[0].unreadCount").value(3));
    }

    @Test
    void shouldTranslateCurrentAndSizeParamsWhenListingMessages() throws Exception {
        Page<ChatMessageVO> page = new Page<>(3, 5, 1);
        ChatMessageVO message = new ChatMessageVO();
        message.setId(10L);
        message.setFromUserId(2L);
        message.setToUserId(1L);
        message.setContent("分页兼容消息");
        message.setIsRead(0);
        message.setCreateTime(LocalDateTime.of(2026, 3, 3, 10, 30, 0));
        page.setRecords(List.of(message));
        when(chatService.getMessagePage(any(Page.class), eq(1L), eq(2L))).thenReturn(page);

        mockMvc.perform(get("/chat/message/list")
                        .with(authorizedAs(1L, "APPLICANT"))
                        .param("peerId", "2")
                        .param("current", "3")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].content").value("分页兼容消息"));

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(chatService).getMessagePage(pageCaptor.capture(), eq(1L), eq(2L));
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(3L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(5L);
    }

    @Test
    void shouldListMessagesBySessionIdWhenProvided() throws Exception {
        Page<ChatMessageVO> page = new Page<>(1, 20, 1);
        ChatMessageVO message = new ChatMessageVO();
        message.setId(11L);
        message.setFromUserId(2L);
        message.setToUserId(1L);
        message.setContent("按会话ID加载");
        message.setIsRead(1);
        message.setCreateTime(LocalDateTime.of(2026, 3, 4, 9, 30, 0));
        page.setRecords(List.of(message));
        when(chatService.getMessagePage(any(Page.class), eq(1L), isNull(), eq(22L))).thenReturn(page);

        mockMvc.perform(get("/chat/message/list")
                        .with(authorizedAs(1L, "APPLICANT"))
                        .param("sessionId", "22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].content").value("按会话ID加载"));

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(chatService).getMessagePage(pageCaptor.capture(), eq(1L), isNull(), eq(22L));
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20L);
    }

    @Test
    void shouldRejectBlankMessageWhenSendingChatMessage() throws Exception {
        mockMvc.perform(post("/chat/message/send")
                        .with(authorizedAs(1L, "APPLICANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "toUserId": 2,
                                  "content": "   "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("消息内容不能为空"));
    }

    @Test
    void shouldSendChatMessageSuccessfully() throws Exception {
        ChatMessage message = new ChatMessage();
        message.setId(88L);
        message.setFromUserId(1L);
        message.setToUserId(2L);
        message.setContent("你好，世界");
        message.setIsRead(0);
        message.setCreateTime(LocalDateTime.of(2026, 3, 3, 11, 0, 0));
        when(chatService.sendMessageWithPush(1L, 2L, "你好，世界")).thenReturn(message);

        mockMvc.perform(post("/chat/message/send")
                        .with(authorizedAs(1L, "APPLICANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "toUserId": 2,
                                  "content": "你好，世界"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(88))
                .andExpect(jsonPath("$.data.content").value("你好，世界"))
                .andExpect(jsonPath("$.data.isRead").value(0));
    }

    @Test
    void shouldRejectMissingSessionIdentityWhenMarkingChatRead() throws Exception {
        mockMvc.perform(put("/chat/message/read")
                        .with(authorizedAs(1L, "APPLICANT"))
                        .contentType(json())
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("缺少会话标识"));
    }

    @Test
    void shouldMarkChatReadSuccessfully() throws Exception {
        when(chatService.markRead(1L, 2L)).thenReturn(true);

        mockMvc.perform(put("/chat/message/read")
                        .with(authorizedAs(1L, "APPLICANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "peerId": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void shouldMarkChatReadBySessionIdSuccessfully() throws Exception {
        when(chatService.markRead(1L, null, 22L)).thenReturn(true);

        mockMvc.perform(put("/chat/message/read")
                        .with(authorizedAs(1L, "APPLICANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "sessionId": 22
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void shouldRejectSessionJobUpdateWhenServiceReturnsNull() throws Exception {
        when(chatService.updateSessionJob(2L, 1L, 100L, null)).thenReturn(null);

        mockMvc.perform(put("/chat/session/job")
                        .with(authorizedAs(2L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "peerId": 1,
                                  "jobId": 100
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("更新会话岗位失败"));
    }

    @Test
    void shouldUpdateSessionJobSuccessfully() throws Exception {
        ChatSession session = new ChatSession();
        session.setId(66L);
        session.setApplicantId(1L);
        session.setMerchantId(2L);
        session.setJobId(100L);
        session.setJobTitle("测试岗位");
        session.setJobKey("JOB:100");
        when(chatService.updateSessionJob(2L, 1L, 100L, "JOB:100")).thenReturn(session);

        ChatSessionJobUpdateDTO payload = new ChatSessionJobUpdateDTO();
        payload.setPeerId(1L);
        payload.setJobId(100L);
        payload.setJobKey("JOB:100");

        mockMvc.perform(put("/chat/session/job")
                        .with(authorizedAs(2L, "MERCHANT"))
                        .contentType(json())
                        .content(toJson(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value(66))
                .andExpect(jsonPath("$.data.peerId").value(1))
                .andExpect(jsonPath("$.data.jobId").value(100))
                .andExpect(jsonPath("$.data.jobTitle").value("测试岗位"))
                .andExpect(jsonPath("$.data.jobKey").value("JOB:100"));
    }

    @Test
    void shouldUpdateSessionJobBySessionIdSuccessfully() throws Exception {
        ChatSession session = new ChatSession();
        session.setId(88L);
        session.setApplicantId(1L);
        session.setMerchantId(2L);
        session.setJobId(101L);
        session.setJobTitle("按会话切换岗位");
        session.setJobKey("JOB:101");
        when(chatService.updateSessionJob(1L, null, 88L, 101L, "JOB:101")).thenReturn(session);

        mockMvc.perform(put("/chat/session/job")
                        .with(authorizedAs(1L, "APPLICANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "sessionId": 88,
                                  "jobId": 101,
                                  "jobKey": "JOB:101"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(88))
                .andExpect(jsonPath("$.data.sessionId").value(88))
                .andExpect(jsonPath("$.data.peerId").value(2))
                .andExpect(jsonPath("$.data.jobId").value(101))
                .andExpect(jsonPath("$.data.jobTitle").value("按会话切换岗位"))
                .andExpect(jsonPath("$.data.jobKey").value("JOB:101"));
    }
}
