/**
 * @file 速览索引
 * @summary 库存预警分页响应 DTO，用于返回仓库+SKU 维度的预警结果。
 * @core 1. 返回仓库与 SKU 标识字段
 * @core 2. 返回当前库存与 min/safe/max 阈值
 * @core 3. 返回预警类型与预警级别
 * @entry 先看：currentQty、minQty、safeQty、maxQty、alertType、alertLevel
 * @deps 关键依赖：InventoryRepository.alertRowMapper、InventoryService.pageAlerts
 * @state 关键字段：alertType(LOW/HIGH)、alertLevel(CRITICAL/WARN/INFO)
 * @risk 高风险修改点：字段顺序/命名需与 SQL 别名和前端预警表格保持一致
 * @link 相关文件：后端/src/main/java/com/wms/backend/inventory/repository/InventoryRepository.java、后端/src/main/java/com/wms/backend/inventory/controller/InventoryController.java
 */
package com.wms.backend.inventory.dto;

public record InventoryAlertResponse(
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long skuId,
        String skuCode,
        String skuName,
        Integer currentQty,
        Integer minQty,
        Integer safeQty,
        Integer maxQty,
        String alertType,
        String alertLevel
) {
}
