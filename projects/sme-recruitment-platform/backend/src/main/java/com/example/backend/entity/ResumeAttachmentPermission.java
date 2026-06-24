package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 附件简历授权记录
 */
@Data
@TableName("resume_attachment_permission")
public class ResumeAttachmentPermission {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("applicant_id")
    private Long applicantId; // 授权人（求职者）

    @TableField("merchant_id")
    private Long merchantId; // 被授权人（商家）

    @TableField("attachment_type")
    private String attachmentType; // 简历类型（默认 RESUME）

    @TableField("status")
    private Integer status; // 状态：0-待同意，1-已授权，2-已拒绝

    @TableField("expire_time")
    private LocalDateTime expireTime; // 授权有效期（可为空）

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
