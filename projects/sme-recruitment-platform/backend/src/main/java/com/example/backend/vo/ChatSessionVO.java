/**
 * 文件速览：
 * 1. 文件职责：定义聊天会话列表返回结构。
 * 2. 关键升级：id 与 sessionId 现在都围绕真实 chat_session 主键组织。
 * 3. 关键字段：id、sessionId、peerId、jobId、lastMessage、unreadCount。
 * 4. 阅读建议：结合 ChatServiceImpl.getSessionList 查看字段填充优先级。
 */
package com.example.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表返回结构
 */
@Data
public class ChatSessionVO {
    private Long id;               // 会话主键（优先使用 chat_session.id）
    private Long sessionId;        // 会话记录ID（chat_session）
    private Long peerId;           // 对方用户ID
    private String peerName;       // 对方展示名
    private String companyName;    // 对方公司名（商家端）
    private String peerAvatar;     // 对方头像
    private Long jobId;            // 沟通岗位ID（可为空）
    private String jobTitle;       // 沟通职位（如有）
    private String jobKey;         // 沟通岗位关键字（可变更）
    private String lastMessage;    // 最近一条消息
    private LocalDateTime lastTime;// 最近一条消息时间
    private Integer unreadCount;   // 未读数
}
