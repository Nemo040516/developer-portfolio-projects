package com.example.backend.dto;

import lombok.Data;

/**
 * WebSocket 推送给接收方的消息体
 */
@Data
public class ChatWsPushDTO {
    private Long fromUserId;
    private Long toUserId;
    private String content;
    private String createTime;
    private String senderName;
    private String senderAvatar;
}
