package com.example.backend.dto;

import lombok.Data;

/**
 * WebSocket 发送消息体
 */
@Data
public class ChatWsSendDTO {
    private Long toUserId;
    private String content;
}
