package com.example.backend.vo;

import lombok.Data;

/**
 * 未读数量查询结果（内部使用）
 */
@Data
public class ChatUnreadCountRow {
    private Long peerId;
    private Integer unreadCount;
}
