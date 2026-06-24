/*
 * 文件速览：
 * 1. 文件职责：承载管理员更新账号封禁状态时的请求体。
 * 2. 对外入口：AdminController.updateUserBan。
 * 3. 关键结构：约束封禁状态范围与原因长度，避免非法状态值进入业务层。
 * 4. 阅读建议：结合 AdminController 和 AdminServiceImpl.updateUserBan 一起看。
 */
package com.example.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员更新账号封禁状态 DTO
 */
@Data
public class AdminUserBanDTO {
    @NotNull(message = "封禁状态不能为空")
    @Min(value = 0, message = "封禁状态不合法")
    @Max(value = 2, message = "封禁状态不合法")
    private Integer banStatus; // 0-正常, 1-限制, 2-封禁/拉黑

    @Size(max = 200, message = "封禁原因不能超过200字")
    private String banReason;  // 封禁原因
    private LocalDateTime banUntil; // 封禁截止时间（可为空）
}
