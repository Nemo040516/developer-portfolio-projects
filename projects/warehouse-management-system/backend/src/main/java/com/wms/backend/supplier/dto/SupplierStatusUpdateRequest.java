/**
 * @file 速览索引
 * @summary 供应商状态变更请求 DTO，约束供应商启停状态入参。
 * @core 1. 承载 status 字段
 * @core 2. 约束状态必填
 * @core 3. 限定状态仅允许 0/1
 * @entry 先看：status
 * @deps 关键依赖：SupplierController.updateStatus、SupplierService.updateStatus
 * @state 关键字段：status(@Min(0) @Max(1))
 * @risk 高风险修改点：状态范围变更会影响供应商可用性与下游单据选择范围
 * @link 相关文件：后端/src/main/java/com/wms/backend/supplier/service/SupplierService.java、后端/src/main/java/com/wms/backend/supplier/controller/SupplierController.java
 */
package com.wms.backend.supplier.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SupplierStatusUpdateRequest(
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态值不合法")
        @Max(value = 1, message = "状态值不合法")
        Integer status
) {
}
