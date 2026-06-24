package com.example.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 管理员批量审核 DTO
 */
@Data
public class AdminBatchAuditDTO {
    @NotEmpty(message = "审核ID不能为空")
    private List<Long> ids;

    /**
     * 审核状态：1-通过，2-驳回
     */
    @NotNull(message = "审核状态不能为空")
    private Integer status;

    /**
     * 驳回原因（通过时可为空）
     */
    private String reason;
}
