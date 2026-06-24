package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 即时通讯聊天记录表实体
 */
@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("from_user_id")
    private Long fromUserId; // 发送者ID

    @TableField("to_user_id")
    private Long toUserId;   // 接收者ID

    private String content;  // 消息内容

    @TableField("is_read")
    private Integer isRead;  // 是否已读: 0-未读, 1-已读

    @TableField("job_id")
    private Long jobId; // 沟通岗位ID（可为空）

    @TableField("job_key")
    private String jobKey; // 沟通岗位关键字（可变更）

    @TableField("create_time")
    private LocalDateTime createTime; // 发送时间
}
