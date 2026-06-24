package com.example.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员审核通用 DTO
 */
@Data
public class AdminAuditDTO {
    /**
     * 审核状态：1-通过，2-驳回
     */
    @NotNull(message = "审核状态不能为空")
    @Min(value = 1, message = "审核状态不合法")
    @Max(value = 2, message = "审核状态不合法")
    private Integer status;

    /**
     * 驳回原因（通过时可为空）
     */
    @Size(max = 500, message = "驳回原因不能超过500字")
    private String reason;
}
