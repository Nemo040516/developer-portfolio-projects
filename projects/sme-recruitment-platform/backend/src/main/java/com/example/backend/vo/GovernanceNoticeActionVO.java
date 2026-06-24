/*
 * 文件速览：
 * 1. 文件职责：承载治理事项时间线动作的展示视图。
 * 2. 对外入口：供治理事项详情页展示已读、整改提交、复核记录。
 * 3. 关键结构：actorRole、actionType、actorName、content。
 * 4. 阅读建议：优先关注 actionType 与 createTime 的时间线语义。
 */
package com.example.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 治理事项动作 VO
 */
@Data
public class GovernanceNoticeActionVO {
    private Long id;
    private Long noticeId;
    private String actorRole;
    private Long actorUserId;
    private String actorName;
    private String actionType;
    private String content;
    private String attachmentJson;
    private String extraJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
