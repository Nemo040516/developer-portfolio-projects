/*
 * 文件速览：
 * 1. 文件职责：承载聊天 REST 兜底发送消息的请求体。
 * 2. 对外入口：ChatController.sendMessage。
 * 3. 关键结构：约束目标用户 ID 与消息内容，避免空消息或非法目标进入业务层。
 * 4. 阅读建议：结合 ChatController 与 ChatRestApiTest 一起看。
 */
package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * REST 发送消息 DTO（用于断线兜底）
 */
@Data
public class ChatSendDTO {
    @NotNull(message = "对方用户ID不能为空")
    @Positive(message = "对方用户ID必须为正数")
    private Long toUserId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过2000字")
    private String content;
}
