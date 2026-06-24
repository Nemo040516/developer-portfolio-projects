/**
 * @file 速览索引
 * @summary 库存预警规则响应 DTO，返回规则主键、阈值、状态与审计字段。
 * @core 1. 描述仓库+SKU 维度的预警规则
 * @core 2. 返回 min/safe/max 阈值与状态
 * @core 3. 返回 createdBy/createdAt/updatedAt 审计信息
 * @entry 先看：warehouseId、skuId、minQty、safeQty、maxQty、status
 * @deps 关键依赖：InventoryRepository.alertRuleRowMapper、InventoryService.pageAlertRules
 * @state 关键字段：status(0 停用/1 启用)、remark、时间字段
 * @risk 高风险修改点：字段口径变更会影响规则列表回显与编辑回填
 * @link 相关文件：后端/src/main/java/com/wms/backend/inventory/repository/InventoryRepository.java、后端/src/main/java/com/wms/backend/inventory/service/InventoryService.java
 */
package com.wms.backend.inventory.dto;

import java.time.LocalDateTime;

public record InventoryAlertRuleResponse(
        Long id,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long skuId,
        String skuCode,
        String skuName,
        Integer minQty,
        Integer safeQty,
        Integer maxQty,
        Integer status,
        String remark,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
