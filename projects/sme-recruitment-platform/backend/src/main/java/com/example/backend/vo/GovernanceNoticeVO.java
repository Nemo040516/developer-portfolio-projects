/*
 * 文件速览：
 * 1. 文件职责：承载商家 / 求职者侧治理事项详情与列表的统一展示视图。
 * 2. 对外入口：供后续 /governance/notices/my 及详情接口返回。
 * 3. 关键结构：事项主体、动作权限、时间线列表。
 * 4. 阅读建议：先看 canAcknowledge / canReply，再看 actions。
 */
package com.example.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户侧治理通知 VO
 */
@Data
public class GovernanceNoticeVO {
    private Long id;
    private String noticeNo;
    private String noticeType;
    private String severity;
    private String sourceModule;
    private Long sourceId;
    private Long relatedJobId;
    private String relatedJobTitle;
    private String title;
    private String summary;
    private String detail;
    private String requiredAction;
    private String status;
    private Integer needAck;
    private Integer needReply;
    private Boolean canAcknowledge;
    private Boolean canReply;
    private Boolean canAppeal;
    private List<GovernanceNoticeActionVO> actions;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime dueTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime readTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime latestActionTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
