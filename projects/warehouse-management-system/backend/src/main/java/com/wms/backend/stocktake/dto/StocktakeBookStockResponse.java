/**
 * @file 速览索引
 * @summary 盘点账面库存响应 DTO，返回盘点建单时可选库位账面库存列表。
 * @core 1. 返回仓库/库位/SKU 标识信息
 * @core 2. 返回库位编码与库区名称
 * @core 3. 返回账面数量 bookQty 作为盘点初始参考
 * @entry 先看：locationId、locationCode、skuId、bookQty
 * @deps 关键依赖：StocktakeRepository.bookStockRowMapper/listBookStocks、StocktakeService.bookStocks
 * @state 关键字段：warehouseId、locationId、skuId、bookQty
 * @risk 高风险修改点：字段口径变更会影响盘点建单“带出账面库存”能力与前端选择弹窗
 * @link 相关文件：后端/src/main/java/com/wms/backend/stocktake/repository/StocktakeRepository.java、后端/src/main/java/com/wms/backend/stocktake/controller/StocktakeController.java
 */
package com.wms.backend.stocktake.dto;

public record StocktakeBookStockResponse(
        Long warehouseId,
        Long locationId,
        String locationCode,
        String areaName,
        Long skuId,
        String skuCode,
        String skuName,
        Integer bookQty
) {
}
