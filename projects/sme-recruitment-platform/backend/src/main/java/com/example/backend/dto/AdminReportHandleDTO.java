package com.example.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员处理举报 DTO
 */
@Data
public class AdminReportHandleDTO {
    /**
     * 处理状态：1-已处理，2-已驳回
     */
    @NotNull(message = "处理状态不能为空")
    private Integer status;

    /**
     * 处理动作（建议使用代码）：
     * JOB_WARN / JOB_OFFLINE / JOB_OFFLINE_LIMIT_MERCHANT / JOB_OFFLINE_BAN_MERCHANT
     * / MERCHANT_WARN / MERCHANT_LIMIT / MERCHANT_BAN
     * / USER_WARN / USER_DISABLE / USER_BAN / USER_BLACKLIST
     * / REJECT（status=2时）
     */
    private String action;

    /**
     * 处理结果说明
     */
    private String result;
}
