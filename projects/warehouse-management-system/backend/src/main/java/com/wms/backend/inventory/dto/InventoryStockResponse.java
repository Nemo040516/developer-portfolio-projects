/**
 * @file 速览索引
 * @summary 库存汇总分页响应 DTO，返回仓库+SKU 维度的当前库存台账。
 * @core 1. 返回库存记录主键与仓库/SKU 标识
 * @core 2. 返回当前在手库存 onHandQty
 * @core 3. 返回库存更新时间 updatedAt
 * @entry 先看：warehouseCode、skuCode、onHandQty、updatedAt
 * @deps 关键依赖：InventoryRepository.stockRowMapper、InventoryService.pageStocks
 * @state 关键字段：onHandQty、warehouseId、skuId
 * @risk 高风险修改点：字段命名需与库存列表 SQL 别名及前端列配置保持一致
 * @link 相关文件：后端/src/main/java/com/wms/backend/inventory/repository/InventoryRepository.java、后端/src/main/java/com/wms/backend/inventory/controller/InventoryController.java
 */
package com.wms.backend.inventory.dto;

import java.time.LocalDateTime;

public record InventoryStockResponse(
        Long id,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long skuId,
        String skuCode,
        String skuName,
        Integer onHandQty,
        LocalDateTime updatedAt
) {
}
