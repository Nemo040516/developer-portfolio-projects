package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话上下文（岗位信息）
 */
@Data
@TableName("chat_session")
public class ChatSession {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("applicant_id")
    private Long applicantId; // 求职者ID

    @TableField("merchant_id")
    private Long merchantId; // 商家ID

    @TableField("job_id")
    private Long jobId; // 当前沟通岗位ID

    @TableField("job_title")
    private String jobTitle; // 当前沟通岗位名称

    @TableField("job_key")
    private String jobKey; // 岗位关键字（可变更）

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
