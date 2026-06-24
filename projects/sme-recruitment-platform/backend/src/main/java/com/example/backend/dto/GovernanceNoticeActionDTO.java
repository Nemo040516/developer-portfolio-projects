/*
 * 文件速览：
 * 1. 文件职责：承载商家 / 求职者提交治理事项反馈、整改说明或申诉的请求体。
 * 2. 对外入口：供后续 /governance/notices/{id}/actions 接口使用。
 * 3. 关键结构：actionType、content、attachmentJson。
 * 4. 阅读建议：Phase 1 只启用 SUBMIT_FIX，后续再扩展 REPLY / APPEAL。
 */
package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户治理事项动作 DTO
 */
@Data
public class GovernanceNoticeActionDTO {

    @NotBlank(message = "动作类型不能为空")
    private String actionType;

    @Size(max = 2000, message = "说明长度不能超过2000")
    private String content;

    private String attachmentJson;
}
