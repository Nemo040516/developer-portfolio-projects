/**
 * @file 速览索引
 * @summary 盘点明细请求 DTO，承载 SKU+库位+实盘数量并限制数量范围。
 * @core 1. 约束 skuId/locationId 必填
 * @core 2. 约束 countQty 必填且不能小于0
 * @core 3. 承载差异原因与备注文本
 * @entry 先看：skuId、locationId、countQty
 * @deps 关键依赖：StocktakeCreateRequest/StocktakeUpdateRequest、StocktakeService.insertItems/validateMasterData
 * @state 关键字段：countQty(@Min(0))、reason(max=128)、remark(max=255)
 * @risk 高风险修改点：数量字段语义变更会影响差异计算与确认回写库存逻辑
 * @link 相关文件：后端/src/main/java/com/wms/backend/stocktake/service/StocktakeService.java、后端/src/main/java/com/wms/backend/stocktake/dto/StocktakeCreateRequest.java
 */
package com.wms.backend.stocktake.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StocktakeItemRequest(
        @NotNull(message = "SKU不能为空")
        Long skuId,
        @NotNull(message = "库位不能为空")
        Long locationId,
        @NotNull(message = "实盘数量不能为空")
        @Min(value = 0, message = "实盘数量不能小于0")
        Integer countQty,
        @Size(max = 128, message = "差异原因长度不能超过128")
        String reason,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark
) {
}
