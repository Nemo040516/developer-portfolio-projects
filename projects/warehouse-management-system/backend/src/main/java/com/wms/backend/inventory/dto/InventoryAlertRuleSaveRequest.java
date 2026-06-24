/**
 * @file 速览索引
 * @summary 库存预警规则保存请求 DTO，用于新增/编辑规则的入参校验。
 * @core 1. 承载仓库、SKU、阈值、状态等可编辑字段
 * @core 2. 通过注解约束必填项与非负数阈值
 * @core 3. 作为预警规则新增与更新接口统一请求体
 * @entry 先看：warehouseId、skuId、minQty、safeQty、maxQty、status
 * @deps 关键依赖：InventoryController.createAlertRule/updateAlertRule、InventoryService.validateAlertRuleRequest
 * @state 关键字段：minQty/safeQty/maxQty、remark(max=255)
 * @risk 高风险修改点：注解或字段调整会直接影响接口参数校验与前端提交结构
 * @link 相关文件：后端/src/main/java/com/wms/backend/inventory/service/InventoryService.java、后端/src/main/java/com/wms/backend/inventory/controller/InventoryController.java
 */
package com.wms.backend.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InventoryAlertRuleSaveRequest(
        @NotNull(message = "仓库不能为空")
        Long warehouseId,
        @NotNull(message = "SKU不能为空")
        Long skuId,
        @NotNull(message = "预警下限不能为空")
        @Min(value = 0, message = "预警下限不能小于0")
        Integer minQty,
        @NotNull(message = "安全库存不能为空")
        @Min(value = 0, message = "安全库存不能小于0")
        Integer safeQty,
        @NotNull(message = "预警上限不能为空")
        @Min(value = 0, message = "预警上限不能小于0")
        Integer maxQty,
        @NotNull(message = "状态不能为空")
        Integer status,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark
) {
}
