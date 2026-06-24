/**
 * @file 速览索引
 * @summary 库存流水分页响应 DTO，承载业务单据引发的库存变更轨迹。
 * @core 1. 返回业务类型、业务单号与业务主键
 * @core 2. 返回仓库/SKU 维度的前后库存与变更量
 * @core 3. 返回操作人、发生时间与备注
 * @entry 先看：bizType、bizNo、qtyChange、beforeQty、afterQty、occurredAt
 * @deps 关键依赖：InventoryRepository.txnRowMapper、InventoryService.pageTxns
 * @state 关键字段：operatorId/operatorName、remark、warehouseId、skuId
 * @risk 高风险修改点：字段变更会影响库存流水查询 UNION 结果映射与前端流水展示
 * @link 相关文件：后端/src/main/java/com/wms/backend/inventory/repository/InventoryRepository.java、后端/src/main/java/com/wms/backend/inventory/controller/InventoryController.java
 */
package com.wms.backend.inventory.dto;

import java.time.LocalDateTime;

public record InventoryTxnResponse(
        Long id,
        String bizType,
        String bizNo,
        Long bizId,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
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
