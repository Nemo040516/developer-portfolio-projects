/*
 * 文件速览：
 * 1. 文件职责：承接面试状态更新请求，覆盖确认、拒绝、取消、完成等动作。
 * 2. 对外入口：InterviewScheduleController.updateStatus。
 * 3. 关键结构：约束面试安排 ID 与状态范围，避免非法状态写入。
 * 4. 阅读建议：结合 InterviewScheduleServiceImpl.updateStatus 一起看。
 */
package com.example.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 面试状态更新请求
 */
@Data
public class InterviewStatusUpdateDTO {
    @NotNull(message = "面试安排ID不能为空")
    @Positive(message = "面试安排ID必须为正数")
    private Long id;      // 面试安排ID

    @NotNull(message = "面试状态不能为空")
    @Min(value = 1, message = "面试状态不合法")
    @Max(value = 4, message = "面试状态不合法")
    private Integer status; // 新状态
}
