/*
 * 文件速览：
 * 1. 文件职责：承接求职者投递职位时的最小请求体，仅负责接收职位 ID。
 * 2. 对外入口：JobDeliveryController.submit。
 * 3. 关键结构：对职位 ID 做非空与正数校验，避免非法参数直接进入业务层。
 * 4. 阅读建议：结合 JobDeliveryController 与 DeliveryFlowApiTest 一起看。
 */
package com.example.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class DeliverySubmitDTO {
    @NotNull(message = "职位ID不能为空")
    @Positive(message = "职位ID必须为正数")
    private Long jobId; // 前端只需传职位ID
}
