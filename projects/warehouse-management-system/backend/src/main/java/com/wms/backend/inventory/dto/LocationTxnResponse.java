/**
 * @file 速览索引
 * @summary 库位流水分页响应 DTO，承载库位级库存变化的业务轨迹。
 * @core 1. 返回业务类型、业务单号与业务主键
 * @core 2. 返回仓库/库位/SKU 维度的变更前后数量
 * @core 3. 返回操作人与发生时间用于追溯
 * @entry 先看：bizType、bizNo、locationCode、qtyChange、beforeQty、afterQty、occurredAt
 * @deps 关键依赖：InventoryRepository.locationTxnRowMapper、InventoryService.pageLocationTxns
 * @state 关键字段：warehouseId、locationId、skuId、operatorName、remark
 * @risk 高风险修改点：字段口径变更会影响库位流水 SQL 映射与前端流水展示
 * @link 相关文件：后端/src/main/java/com/wms/backend/inventory/repository/InventoryRepository.java、后端/src/main/java/com/wms/backend/inventory/controller/InventoryController.java
 */
package com.wms.backend.inventory.dto;

import java.time.LocalDateTime;

public record LocationTxnResponse(
        Long id,
        String bizType,
        String bizNo,
        Long bizId,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long locationId,
        String locationCode,
        String areaName,
        Long skuId,
        String skuCode,
        String skuName,
        Integer qtyChange,
        Integer beforeQty,
        Integer afterQty,
        Long operatorId,
        String operatorName,
        LocalDateTime occurredAt,
        String remark
) {
}
