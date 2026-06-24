package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感操作审计日志
 */
@Data
@TableName("sys_log_audit")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模块：JOB / MERCHANT / REPORT / AUTH 等
     */
    private String module;

    /**
     * 动作：CREATE / UPDATE / AUDIT / RESUBMIT / STATUS / HANDLE 等
     */
    private String action;

    /**
     * 目标ID（职位ID/商家ID/举报ID等）
     */
    @TableField("target_id")
    private Long targetId;

    /**
     * 操作者ID（sys_user.id）
     */
    @TableField("operator_id")
    private Long operatorId;

    /**
     * 操作者角色（ADMIN/MERCHANT/APPLICANT）
     */
    @TableField("operator_role")
    private String operatorRole;

    /**
     * 操作详情（可存摘要/JSON）
     */
    private String detail;

    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
