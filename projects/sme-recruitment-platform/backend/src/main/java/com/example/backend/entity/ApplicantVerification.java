package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("applicant_verification")
public class ApplicantVerification {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("real_name")
    private String realName;

    @TableField("cert_type")
    private String certType;

    @TableField("cert_no")
    private String certNo;

    @TableField("remark")
    private String remark;

    /** PENDING/APPROVED/REJECTED */
    @TableField("status")
    private String status;

    @TableField("submit_time")
    private LocalDateTime submitTime;

    @TableField("audit_time")
    private LocalDateTime auditTime;

    @TableField("audit_user_id")
    private Long auditUserId;

    @TableField("audit_reason")
    private String auditReason;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
