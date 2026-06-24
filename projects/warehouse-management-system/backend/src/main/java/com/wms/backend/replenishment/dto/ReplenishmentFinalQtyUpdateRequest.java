/**
 * @file 速览索引
 * @summary 补货明细最终量更新请求 DTO，约束人工干预数量输入。
 * @core 1. 承载 finalQty 字段
 * @core 2. 约束 finalQty 必填
 * @core 3. 约束 finalQty 非负
 * @entry 先看：finalQty
 * @deps 关键依赖：ReplenishmentController.updateFinalQty、ReplenishmentService.updateFinalQty
 * @state 关键字段：finalQty(@NotNull @Min(0))
 * @risk 高风险修改点：数量下限规则变化会影响干预率统计与建议采纳口径
 * @link 相关文件：后端/src/main/java/com/wms/backend/replenishment/service/ReplenishmentService.java、后端/src/main/java/com/wms/backend/replenishment/repository/ReplenishmentRepository.java
 */
package com.wms.backend.replenishment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * M6 明细最终量更新请求：
 * 1) 仅允许非负整数；
 * 2) 仅草稿状态允许更新。
 */
public record ReplenishmentFinalQtyUpdateRequest(
        @NotNull(message = "最终量不能为空")
        @Min(value = 0, message = "最终量不能小于0")
        Integer finalQty
) {
}
