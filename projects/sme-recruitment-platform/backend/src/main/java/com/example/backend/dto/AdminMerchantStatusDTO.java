package com.example.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员更新商家发布状态 DTO
 */
@Data
public class AdminMerchantStatusDTO {
    /**
     * 发布状态：0-限制发布，1-正常，2-封禁
     */
    @NotNull(message = "发布状态不能为空")
    private Integer status;

    /**
     * 处理原因（可选）
     */
    private String reason;
}
