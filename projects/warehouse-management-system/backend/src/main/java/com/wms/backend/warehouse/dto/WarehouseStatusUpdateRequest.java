/**
 * @file 速览索引
 * @summary 仓库状态变更请求 DTO，约束仓库启停状态参数。
 * @core 1. 承载 status 字段
 * @core 2. 约束状态必填
 * @core 3. 限定状态仅允许 0/1
 * @entry 先看：status
 * @deps 关键依赖：WarehouseController.updateStatus、WarehouseService.updateStatus
 * @state 关键字段：status(@NotNull @Min(0) @Max(1))
 * @risk 高风险修改点：状态范围调整会影响仓库可选范围与库存业务入口
 * @link 相关文件：后端/src/main/java/com/wms/backend/warehouse/service/WarehouseService.java、后端/src/main/java/com/wms/backend/warehouse/controller/WarehouseController.java
 */
package com.wms.backend.warehouse.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WarehouseStatusUpdateRequest(
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态值不合法")
        @Max(value = 1, message = "状态值不合法")
        Integer status
) {
}
