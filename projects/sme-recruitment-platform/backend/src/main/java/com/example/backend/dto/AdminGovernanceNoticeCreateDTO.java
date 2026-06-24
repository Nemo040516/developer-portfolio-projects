/*
 * 文件速览：
 * 1. 文件职责：承载管理员创建治理通知 / 整改单的请求体。
 * 2. 对外入口：供后续 /admin/governance/notices 创建接口直接使用。
 * 3. 关键结构：目标对象、来源对象、通知内容、时限要求。
 * 4. 阅读建议：先看必填字段，再看 Phase 1 只会启用的 relatedJobId / dueTime。
 */
package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员创建治理通知 DTO
 */
@Data
public class AdminGovernanceNoticeCreateDTO {

    @NotBlank(message = "目标角色不能为空")
    private String targetRole;

    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;

    @NotBlank(message = "通知类型不能为空")
    private String noticeType;

    @NotBlank(message = "严重级别不能为空")
    private String severity;

    @NotBlank(message = "来源模块不能为空")
    private String sourceModule;

    private Long sourceId;

    private Long relatedJobId;

    private Long relatedMerchantId;

    @NotBlank(message = "通知标题不能为空")
    @Size(max = 120, message = "通知标题长度不能超过120")
    private String title;

    @Size(max = 255, message = "通知摘要长度不能超过255")
    private String summary;

    @NotBlank(message = "详细说明不能为空")
    private String detail;

    @Size(max = 255, message = "平台要求长度不能超过255")
    private String requiredAction;

    private LocalDateTime dueTime;

    @NotNull(message = "是否需要确认已读不能为空")
    private Integer needAck;

    @NotNull(message = "是否需要用户反馈不能为空")
    private Integer needReply;
}
