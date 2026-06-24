/**
 * @file 速览索引
 * @summary 盘点明细响应 DTO，返回每行 SKU/库位与账实差异信息。
 * @core 1. 返回 SKU 与库位标识字段
 * @core 2. 返回账面数量、实盘数量与差异数量
 * @core 3. 返回差异原因与备注
 * @entry 先看：bookQty、countQty、diffQty、reason
 * @deps 关键依赖：StocktakeRepository.itemRowMapper、StocktakeService.detail/confirm
 * @state 关键字段：locationId、skuId、bookQty/countQty/diffQty
 * @risk 高风险修改点：差异字段口径变化会影响确认盘点的库存修正与流水记录
 * @link 相关文件：后端/src/main/java/com/wms/backend/stocktake/repository/StocktakeRepository.java、后端/src/main/java/com/wms/backend/stocktake/dto/StocktakeDetailResponse.java
 */
package com.wms.backend.stocktake.dto;

public record StocktakeItemResponse(
        Long id,
        Long skuId,
        String skuCode,
        String skuName,
        Long locationId,
        String locationCode,
        String areaName,
        Integer bookQty,
        Integer countQty,
        Integer diffQty,
        String reason,
        String remark
) {
}
