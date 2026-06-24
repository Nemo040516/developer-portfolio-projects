/**
 * 文件速览：
 * 1. 文件职责：提供聊天模块 REST 接口，包括会话列表、消息分页、已读同步与岗位切换。
 * 2. 关键升级：消息分页、已读与岗位切换现支持 sessionId 优先、peerId 兼容。
 * 3. 关键入口：/chat/session/list、/chat/message/list、/chat/message/read、/chat/session/job。
 * 4. 阅读建议：先看参数校验分支，再看 sessionId 与 peerId 的兼容调用路径。
 */
package com.example.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.Result;
import com.example.backend.dto.ChatReadDTO;
import com.example.backend.dto.ChatSendDTO;
import com.example.backend.vo.ChatSessionVO;
import com.example.backend.service.ChatService;
import com.example.backend.support.ChatPresentationSupport;
import com.example.backend.utils.ControllerAccessUtils;
import com.example.backend.vo.ChatMessageVO;
import com.example.backend.dto.ChatSessionJobUpdateDTO;
import com.example.backend.entity.ChatSession;
import com.example.backend.entity.ChatMessage;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 即时通讯接口
 */
@RestController
@Validated
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatPresentationSupport chatPresentationSupport;

    private Long requireLogin() {
        return ControllerAccessUtils.requireLogin();
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/session/list")
    public Result<List<ChatSessionVO>> getSessionList() {
        Long userId = requireLogin();
        return Result.success(chatService.getSessionList(userId));
    }

    /**
     * 获取消息列表（分页）
     */
    @GetMapping("/message/list")
    public Result<IPage<ChatMessageVO>> getMessageList(
            @RequestParam(required = false) Long peerId,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long sessionId
    ) {
        Long userId = requireLogin();
        if (peerId == null && sessionId == null) {
            return Result.error(400, "缺少会话标识");
        }

        int safePageNum = pageNum != null ? pageNum : (current != null ? current : 1);
        int safePageSize = pageSize != null ? pageSize : (size != null ? size : 20);

        Page<ChatMessageVO> page = new Page<>(safePageNum, safePageSize);
        IPage<ChatMessageVO> data = sessionId != null
                ? chatService.getMessagePage(page, userId, peerId, sessionId)
                : chatService.getMessagePage(page, userId, peerId);
        return Result.success(data);
    }

    /**
     * REST 兜底发送消息（断线时使用）
     */
    @PostMapping("/message/send")
    public Result<ChatMessageVO> sendMessage(@RequestBody @Valid ChatSendDTO dto) {
        Long userId = requireLogin();
        ChatMessage message = chatService.sendMessageWithPush(userId, dto.getToUserId(), dto.getContent().trim());
        if (message == null) {
            return Result.error(500, "发送失败");
        }
        return Result.success(chatPresentationSupport.toMessageVO(message));
    }

    /**
     * 标记会话为已读
     */
    @PutMapping("/message/read")
    public Result<Boolean> markRead(@RequestBody @Valid ChatReadDTO dto) {
        Long userId = requireLogin();
        if (dto == null || (dto.getPeerId() == null && dto.getSessionId() == null)) {
            return Result.error(400, "缺少会话标识");
        }
        boolean ok = dto.getSessionId() != null
                ? chatService.markRead(userId, dto.getPeerId(), dto.getSessionId())
                : chatService.markRead(userId, dto.getPeerId());
        return Result.success(ok);
    }

    /**
     * 更新会话沟通岗位
     */
    @PutMapping("/session/job")
    public Result<ChatSessionVO> updateSessionJob(@RequestBody @Valid ChatSessionJobUpdateDTO dto) {
        Long userId = requireLogin();
        if (dto == null || dto.getJobId() == null || (dto.getPeerId() == null && dto.getSessionId() == null)) {
            return Result.error(400, "缺少岗位或会话信息");
        }
        ChatSession session = dto.getSessionId() != null
                ? chatService.updateSessionJob(userId, dto.getPeerId(), dto.getSessionId(), dto.getJobId(), dto.getJobKey())
                : chatService.updateSessionJob(userId, dto.getPeerId(), dto.getJobId(), dto.getJobKey());
        if (session == null) {
            return Result.error(400, "更新会话岗位失败");
        }
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setSessionId(session.getId());
        vo.setPeerId(resolvePeerId(userId, dto.getPeerId(), session));
        vo.setJobId(session.getJobId());
        vo.setJobTitle(session.getJobTitle());
        vo.setJobKey(session.getJobKey());
        return Result.success(vo);
    }

    private Long resolvePeerId(Long userId, Long fallbackPeerId, ChatSession session) {
        if (fallbackPeerId != null) {
            return fallbackPeerId;
        }
        if (session == null || userId == null) {
            return null;
        }
        if (Objects.equals(userId, session.getApplicantId())) {
            return session.getMerchantId();
        }
        if (Objects.equals(userId, session.getMerchantId())) {
            return session.getApplicantId();
        }
        return null;
    }
}
