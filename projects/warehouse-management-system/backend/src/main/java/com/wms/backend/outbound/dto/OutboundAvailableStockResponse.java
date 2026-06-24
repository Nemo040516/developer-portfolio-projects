/**
 * @file 速览索引
 * @summary 出库可用库存响应 DTO，用于返回可出库的库位库存候选列表。
 * @core 1. 返回仓库、库位、SKU 标识信息
 * @core 2. 返回库位编码/库区名称用于前端展示
 * @core 3. 返回可用在手数量 onHandQty 用于分配出库
 * @entry 先看：locationId、locationCode、skuId、onHandQty
 * @deps 关键依赖：OutboundRepository.availableStockRowMapper、OutboundService.availableStocks
 * @state 关键字段：warehouseId、locationId、skuId、onHandQty
 * @risk 高风险修改点：字段变更会影响可用库存接口返回与前端明细行自动回填
 * @link 相关文件：后端/src/main/java/com/wms/backend/outbound/repository/OutboundRepository.java、后端/src/main/java/com/wms/backend/outbound/controller/OutboundController.java
 */
package com.wms.backend.outbound.dto;

public record OutboundAvailableStockResponse(
        Long warehouseId,
        Long locationId,
        String locationCode,
        String areaName,
        Long skuId,
        String skuCode,
        String skuName,
        Integer onHandQty
) {
}
