package com.example.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息列表返回结构
 */
@Data
public class ChatMessageVO {
    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private String content;
    private Integer isRead;
    private LocalDateTime createTime;
}
