/**
 * @file 速览索引
 * @summary 库位库存分页响应 DTO，返回仓库-库位-SKU 维度的库存台账。
 * @core 1. 返回仓库与库位标识信息
 * @core 2. 返回 SKU 基础信息与在手库存
 * @core 3. 返回库存更新时间用于排序与展示
 * @entry 先看：warehouseCode、locationCode、skuCode、onHandQty、updatedAt
 * @deps 关键依赖：InventoryRepository.locationStockRowMapper、InventoryService.pageLocationStocks
 * @state 关键字段：warehouseId、locationId、skuId、onHandQty
 * @risk 高风险修改点：字段命名/顺序变更会影响库位库存 SQL 映射与前端表格列
 * @link 相关文件：后端/src/main/java/com/wms/backend/inventory/repository/InventoryRepository.java、后端/src/main/java/com/wms/backend/inventory/controller/InventoryController.java
 */
package com.wms.backend.inventory.dto;

import java.time.LocalDateTime;

public record LocationStockResponse(
        Long id,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long locationId,
        String locationCode,
        String areaName,
        Long skuId,
        String skuCode,
        String skuName,
        Integer onHandQty,
        LocalDateTime updatedAt
) {
}
