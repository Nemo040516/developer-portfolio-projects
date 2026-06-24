/*
 * 文件速览：
 * 1. 文件职责：映射平台治理通知动作表 governance_notice_action。
 * 2. 对外入口：供治理通知 Service 记录已读、整改提交、申诉、复核等动作时间线。
 * 3. 关键结构：noticeId、actorRole、actionType、attachmentJson、extraJson。
 * 4. 阅读建议：先看 actionType，再看 content 与扩展 JSON 字段。
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
 * 平台治理通知动作实体
 */
@Data
@TableName("governance_notice_action")
public class GovernanceNoticeAction {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属治理通知ID
     */
    @TableField("notice_id")
    private Long noticeId;

    /**
     * 操作人角色：ADMIN / MERCHANT / APPLICANT
     */
    @TableField("actor_role")
    private String actorRole;

    /**
     * 操作人ID
     */
    @TableField("actor_user_id")
    private Long actorUserId;

    /**
     * 动作类型：READ / SUBMIT_FIX / REPLY / APPEAL / APPROVE / REJECT / CLOSE
     */
    @TableField("action_type")
    private String actionType;

    /**
     * 动作说明
     */
    private String content;

    /**
     * 附件列表 JSON
     */
    @TableField("attachment_json")
    private String attachmentJson;

    /**
     * 预留扩展字段 JSON
     */
    @TableField("extra_json")
    private String extraJson;

    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
