/**
 * 文件速览：
 * 1. 文件职责：承载聊天会话岗位上下文更新请求。
 * 2. 关键升级：新增 sessionId，支持优先按真实会话标识更新岗位。
 * 3. 关键字段：sessionId、peerId、jobId、jobKey。
 * 4. 阅读建议：先看 sessionId/peerId 的兼容关系，再看 jobId/jobKey 的更新语义。
 */
package com.example.backend.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 更新会话岗位信息请求
 */
@Data
public class ChatSessionJobUpdateDTO {
    @Positive(message = "会话ID必须为正数")
    private Long sessionId; // 会话ID，优先用于定位真实会话

    @Positive(message = "对方用户ID必须为正数")
    private Long peerId;  // 对方用户ID

    @Positive(message = "岗位ID必须为正数")
    private Long jobId;   // 新岗位ID
    private String jobKey; // 可选自定义岗位关键字
}
