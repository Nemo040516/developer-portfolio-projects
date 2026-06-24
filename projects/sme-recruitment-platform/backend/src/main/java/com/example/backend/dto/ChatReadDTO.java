/**
 * 文件速览：
 * 1. 文件职责：承载聊天已读同步请求体。
 * 2. 关键升级：本次将 sessionId 从预留字段升级为正式兼容入参。
 * 3. 关键字段：peerId、sessionId。
 * 4. 阅读建议：结合 ChatController 与 ChatServiceImpl 一起看参数解析逻辑。
 */
package com.example.backend.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 标记已读请求体
 */
@Data
public class ChatReadDTO {
    @Positive(message = "对方用户ID必须为正数")
    private Long peerId;

    @Positive(message = "会话ID必须为正数")
    private Long sessionId; // 会话ID，优先用于定位真实会话
}
