package com.example.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表原始查询结果（内部使用）
 */
@Data
public class ChatSessionRow {
    private Long peerId;
    private String lastMessage;
    private LocalDateTime lastTime;
}
