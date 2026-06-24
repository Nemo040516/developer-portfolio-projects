/*
 * 文件速览：
 * 1. 文件职责：承接商家更新投递状态时的请求体，包括面试邀约附带信息。
 * 2. 对外入口：JobDeliveryController.updateStatus。
 * 3. 关键结构：约束投递记录 ID、状态范围与文本字段长度，降低非法状态流转风险。
 * 4. 阅读建议：先看状态值约束，再看面试邀约附加字段。
 */
package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投递状态更新请求
 */
@Data
public class DeliveryStatusUpdateDTO {
    @NotNull(message = "投递记录ID不能为空")
    @Positive(message = "投递记录ID必须为正数")
    private Long id;         // 投递记录ID

    @NotNull(message = "投递状态不能为空")
    @Min(value = 0, message = "投递状态不合法")
    @Max(value = 3, message = "投递状态不合法")
    private Integer status;  // 新状态

    @Size(max = 500, message = "反馈说明不能超过500字")
    private String feedback; // 可选：反馈/面试备注

    // 面试邀约结构化字段（状态=2 时使用）
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime interviewTime; // 面试时间

    @Size(max = 100, message = "面试地点不能超过100字")
    private String interviewLocation;    // 面试地点

    @Size(max = 20, message = "面试方式不能超过20字")
    private String interviewMethod;      // 面试形式

    @Size(max = 500, message = "面试备注不能超过500字")
    private String interviewRemark;      // 面试备注
}
