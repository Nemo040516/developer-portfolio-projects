/*
 * 文件速览：
 * 1. 文件职责：承接举报提交请求，覆盖举报类型、目标、原因与证据列表。
 * 2. 对外入口：ReportController.submit。
 * 3. 关键结构：对类型、目标 ID、原因长度与证据数量做基础校验。
 * 4. 阅读建议：先看基础字段约束，再看 ReportController 中的归属与关系校验。
 */
package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 举报提交 DTO
 */
@Data
public class ReportSubmitDTO {
    /**
     * 举报类型：JOB / MERCHANT / USER
     */
    @NotBlank(message = "举报类型不能为空")
    private String type;

    /**
     * 被举报对象 ID
     */
    @NotNull(message = "举报对象不能为空")
    @Positive(message = "举报对象ID必须为正数")
    private Long targetId;

    /**
     * 举报原因（含说明）
     */
    @NotBlank(message = "举报原因不能为空")
    @Size(max = 500, message = "举报原因不能超过500字")
    private String reason;

    /**
     * 关联职位 ID（可选，用于校验或辅助说明）
     */
    @Positive(message = "关联职位ID必须为正数")
    private Long jobId;

    /**
     * 举报证据（文件地址列表，可选）
     */
    private List<String> evidenceList;
}
