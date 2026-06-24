/*
 * 文件速览：
 * 1. 文件职责：承载管理员对治理事项进行复核、驳回、关闭的请求体。
 * 2. 对外入口：供后续 /admin/governance/notices/{id}/review 接口使用。
 * 3. 关键结构：目标状态、复核说明、可选关闭动作。
 * 4. 阅读建议：优先关注 reviewStatus 与 reviewComment 的约束。
 */
package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员治理事项复核 DTO
 */
@Data
public class AdminGovernanceNoticeReviewDTO {

    @NotBlank(message = "复核状态不能为空")
    private String reviewStatus;

    @Size(max = 500, message = "复核说明长度不能超过500")
    private String reviewComment;
}
