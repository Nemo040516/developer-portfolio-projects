/*
 * 文件速览：
 * 1. 文件职责：验证聊天服务在落库与切换岗位上下文前，会先校验真实投递关系。
 * 2. 关键入口：ChatServiceImpl#saveMessage、updateSessionJob。
 * 3. 关键结构：无关系拒绝、存在关系允许落库。
 * 4. 阅读建议：先看 saveMessage 的拒绝/放行，再看 updateSessionJob 的拒绝场景。
 */
package com.example.backend.service.impl;

import com.example.backend.entity.ChatMessage;
import com.example.backend.entity.ChatSession;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.ApplicantInfoMapper;
import com.example.backend.mapper.ChatMessageMapper;
import com.example.backend.mapper.ChatSessionMapper;
import com.example.backend.mapper.JobDeliveryMapper;
import com.example.backend.mapper.JobInfoMapper;
import com.example.backend.mapper.MerchantInfoMapper;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.support.ChatPresentationSupport;
import com.example.backend.websocket.ChatSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private MerchantInfoMapper merchantInfoMapper;

    @Mock
    private ApplicantInfoMapper applicantInfoMapper;

    @Mock
    private JobDeliveryMapper jobDeliveryMapper;

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Mock
    private JobInfoMapper jobInfoMapper;

    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl();
        ReflectionTestUtils.setField(chatService, "baseMapper", chatMessageMapper);
        ReflectionTestUtils.setField(chatService, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(chatService, "merchantInfoMapper", merchantInfoMapper);
        ReflectionTestUtils.setField(chatService, "applicantInfoMapper", applicantInfoMapper);
        ReflectionTestUtils.setField(chatService, "jobDeliveryMapper", jobDeliveryMapper);
        ReflectionTestUtils.setField(chatService, "chatSessionMapper", chatSessionMapper);
        ReflectionTestUtils.setField(chatService, "jobInfoMapper", jobInfoMapper);
        ReflectionTestUtils.setField(chatService, "sessionRegistry", new ChatSessionRegistry());
        ReflectionTestUtils.setField(chatService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(chatService, "chatPresentationSupport", new ChatPresentationSupport());
    }

    @Test
    void shouldRejectMessageWithoutDeliveryRelation() {
        when(sysUserMapper.selectById(2L)).thenReturn(buildUser(2L, "MERCHANT"));
        when(sysUserMapper.selectById(1L)).thenReturn(buildUser(1L, "APPLICANT"));
        when(jobDeliveryMapper.countMerchantApplicantRelation(anyLong(), anyLong())).thenReturn(0L);

        ChatMessage message = chatService.saveMessage(2L, 1L, "你好");

        assertNull(message);
        verify(chatSessionMapper, never()).selectOne(any());
        verify(chatMessageMapper, never()).insert(any());
    }

    @Test
    void shouldSaveMessageWhenDeliveryRelationExists() {
        ChatSession session = new ChatSession();
        session.setId(88L);
        when(sysUserMapper.selectById(2L)).thenReturn(buildUser(2L, "MERCHANT"));
        when(sysUserMapper.selectById(1L)).thenReturn(buildUser(1L, "APPLICANT"));
        when(jobDeliveryMapper.countMerchantApplicantRelation(anyLong(), anyLong())).thenReturn(1L);
        when(chatSessionMapper.selectOne(any())).thenReturn(session);
        when(chatMessageMapper.selectLatestSimilarMessage(2L, 1L, "你好", null, null)).thenReturn(null);
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenReturn(1);

        ChatMessage message = chatService.saveMessage(2L, 1L, " 你好 ");

        assertNotNull(message);
        assertEquals("你好", message.getContent());
        verify(chatMessageMapper).insert(any(ChatMessage.class));
    }

    @Test
    void shouldRejectSessionJobUpdateWithoutDeliveryRelation() {
        when(sysUserMapper.selectById(2L)).thenReturn(buildUser(2L, "MERCHANT"));
        when(sysUserMapper.selectById(1L)).thenReturn(buildUser(1L, "APPLICANT"));
        when(jobDeliveryMapper.countMerchantApplicantRelation(anyLong(), anyLong())).thenReturn(0L);

        assertNull(chatService.updateSessionJob(2L, 1L, 100L, "JOB:100"));
        verify(jobInfoMapper, never()).selectById(any());
    }

    private SysUser buildUser(Long id, String role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
