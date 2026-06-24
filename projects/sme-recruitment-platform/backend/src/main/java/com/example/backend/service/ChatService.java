/**
 * 文件速览：
 * 1. 文件职责：定义即时沟通模块的会话列表、消息分页、已读同步与岗位上下文更新能力。
 * 2. 关键升级：保留 peerId 兼容接口，同时补充 sessionId 优先的重载方法。
 * 3. 关键结构：getSessionList、getMessagePage、markRead、updateSessionJob。
 * 4. 阅读建议：先看兼容保留的方法，再看本次新增的 sessionId 重载能力。
 */
package com.example.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.backend.entity.ChatMessage;
import com.example.backend.entity.ChatSession;
import com.example.backend.vo.ChatMessageVO;
import com.example.backend.vo.ChatSessionVO;

import java.util.List;

/**
 * 即时通讯业务接口
 */
public interface ChatService extends IService<ChatMessage> {

    /**
     * 会话列表
     */
    List<ChatSessionVO> getSessionList(Long userId);

    /**
     * 消息分页
     */
    IPage<ChatMessageVO> getMessagePage(Page<?> page, Long userId, Long peerId);

    /**
     * 消息分页（sessionId 优先，peerId 兼容）
     */
    IPage<ChatMessageVO> getMessagePage(Page<?> page, Long userId, Long peerId, Long sessionId);

    /**
     * 标记已读
     */
    boolean markRead(Long userId, Long peerId);

    /**
     * 标记已读（sessionId 优先，peerId 兼容）
     */
    boolean markRead(Long userId, Long peerId, Long sessionId);

    /**
     * 保存消息
     */
    ChatMessage saveMessage(Long fromUserId, Long toUserId, String content);

    /**
     * 发送消息并尝试推送（REST 兜底）
     */
    ChatMessage sendMessageWithPush(Long fromUserId, Long toUserId, String content);

    /**
     * 更新会话岗位（支持切换岗位）
     */
    ChatSession updateSessionJob(Long userId, Long peerId, Long jobId, String jobKey);

    /**
     * 更新会话岗位（sessionId 优先，peerId 兼容）
     */
    ChatSession updateSessionJob(Long userId, Long peerId, Long sessionId, Long jobId, String jobKey);
}
