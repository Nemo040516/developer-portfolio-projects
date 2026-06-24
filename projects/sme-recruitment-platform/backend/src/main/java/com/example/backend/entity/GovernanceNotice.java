/*
 * 文件速览：
 * 1. 文件职责：映射平台治理通知与整改单主表 governance_notice。
 * 2. 对外入口：供治理通知 Mapper / Service 读写通知主数据。
 * 3. 关键结构：目标对象、来源对象、处理要求、状态时间字段。
 * 4. 阅读建议：先看 target/source 关联字段，再看 status / dueTime / latestActionTime。
 */
package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台治理通知主实体
 */
@Data
@TableName("governance_notice")
public class GovernanceNotice {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务编号
     */
    @TableField("notice_no")
    private String noticeNo;

    /**
     * 目标角色：MERCHANT / APPLICANT
     */
    @TableField("target_role")
    private String targetRole;

    /**
     * 目标用户ID（sys_user.id）
     */
    @TableField("target_user_id")
    private Long targetUserId;

    /**
     * 通知类型：JOB_RECTIFY / MERCHANT_RECTIFY / REPORT_RESULT / USER_WARNING / BAN_NOTICE
     */
    @TableField("notice_type")
    private String noticeType;

    /**
     * 严重级别：INFO / WARNING / HIGH
     */
    private String severity;

    /**
     * 来源模块：JOB_AUDIT / MERCHANT_AUDIT / REPORT / RISK_CONTROL
     */
    @TableField("source_module")
    private String sourceModule;

    /**
     * 来源业务ID
     */
    @TableField("source_id")
    private Long sourceId;

    /**
     * 关联职位ID
     */
    @TableField("related_job_id")
    private Long relatedJobId;

    /**
     * 关联商家ID
     */
    @TableField("related_merchant_id")
    private Long relatedMerchantId;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知摘要
     */
    private String summary;

    /**
     * 详细说明
     */
    private String detail;

    /**
     * 平台要求动作
     */
    @TableField("required_action")
    private String requiredAction;

    /**
     * 截止时间
     */
    @TableField("due_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime dueTime;

    /**
     * 当前状态
     */
    private String status;

    /**
     * 是否要求确认已读：0-否，1-是
     */
    @TableField("need_ack")
    private Integer needAck;

    /**
     * 是否要求用户反馈：0-否，1-是
     */
    @TableField("need_reply")
    private Integer needReply;

    /**
     * 创建管理员ID
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 首次已读时间
     */
    @TableField("read_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime readTime;

    /**
     * 关闭时间
     */
    @TableField("closed_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime closedTime;

    /**
     * 最近动作时间
     */
    @TableField("latest_action_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime latestActionTime;

    /**
     * 逻辑删除：0-否，1-是
     */
    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
