/**
 * @file 速览索引
 * @summary 补货建议计算请求 DTO，承载生成建议所需基础参数。
 * @core 1. 约束 warehouseId 必填
 * @core 2. 支持 calcDays/leadTimeDays/safetyDays 可选调参
 * @core 3. 约束 remark 最大长度 255
 * @entry 先看：warehouseId、calcDays、leadTimeDays、safetyDays
 * @deps 关键依赖：ReplenishmentController.calculate、ReplenishmentService.calculate
 * @state 关键字段：warehouseId(@NotNull)、其余参数为空时由服务层回落默认值
 * @risk 高风险修改点：参数默认值与范围校验口径变化会影响建议结果稳定性
 * @link 相关文件：后端/src/main/java/com/wms/backend/replenishment/service/ReplenishmentService.java、后端/src/main/java/com/wms/backend/replenishment/controller/ReplenishmentController.java
 */
package com.wms.backend.replenishment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * M6 计算请求：
 * 1) 必填仓库；
 * 2) 其余参数允许为空，服务层会自动回填默认值。
 */
public record ReplenishmentCalculateRequest(
        @NotNull(message = "仓库不能为空")
        Long warehouseId,
        Integer calcDays,
        Integer leadTimeDays,
        Integer safetyDays,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark
) {
}
