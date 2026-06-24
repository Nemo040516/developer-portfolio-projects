/*
 * 文件速览：
 * 1. 文件职责：承载管理员侧治理事项列表与详情的展示视图。
 * 2. 对外入口：供后续治理通知列表、详情接口返回。
 * 3. 关键结构：主表基础字段 + 目标用户名 + 关联职位标题 + 最近动作摘要。
 * 4. 阅读建议：先看 status / dueTime，再看 targetUserName / relatedJobTitle。
 */
package com.example.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员治理通知 VO
 */
@Data
public class AdminGovernanceNoticeVO {
    private Long id;
    private String noticeNo;
    private String targetRole;
    private Long targetUserId;
    private String targetUserName;
    private String noticeType;
    private String severity;
    private String sourceModule;
    private Long sourceId;
    private Long relatedJobId;
    private String relatedJobTitle;
    private Long relatedMerchantId;
    private String title;
    private String summary;
    private String detail;
    private String requiredAction;
    private String status;
    private Integer needAck;
    private Integer needReply;
    private Long createdBy;
    private String createdByName;
    private Boolean overdue;
    private List<GovernanceNoticeActionVO> actions;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime dueTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime readTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime closedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime latestActionTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
