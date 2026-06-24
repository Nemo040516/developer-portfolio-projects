/**
 * @file 速览索引
 * @summary 盘点单详情响应 DTO，返回盘点主单信息与明细列表。
 * @core 1. 返回单号、仓库、范围类型与状态
 * @core 2. 返回备注与审计字段
 * @core 3. 返回明细集合 items 供详情页渲染
 * @entry 先看：stocktakeNo、status、scopeType、items
 * @deps 关键依赖：StocktakeService.detail、StocktakeRepository.findOrderById/listItemsByOrderId
 * @state 关键字段：createdBy、createdAt、updatedAt、List<StocktakeItemResponse> items
 * @risk 高风险修改点：主单与明细字段口径变更会影响详情接口与前端回显
 * @link 相关文件：后端/src/main/java/com/wms/backend/stocktake/service/StocktakeService.java、后端/src/main/java/com/wms/backend/stocktake/dto/StocktakeItemResponse.java
 */
package com.wms.backend.stocktake.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StocktakeDetailResponse(
        Long id,
        String stocktakeNo,
        Long warehouseId,
        String warehouseName,
        String scopeType,
        Integer status,
        String remark,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<StocktakeItemResponse> items
) {
}
