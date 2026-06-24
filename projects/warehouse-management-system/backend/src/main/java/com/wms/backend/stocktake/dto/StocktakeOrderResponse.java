/**
 * @file 速览索引
 * @summary 盘点单列表响应 DTO，返回盘点主单分页所需字段。
 * @core 1. 返回盘点单号、仓库信息与范围类型
 * @core 2. 返回状态与备注
 * @core 3. 返回创建人及时间字段用于列表展示
 * @entry 先看：stocktakeNo、warehouseName、scopeType、status
 * @deps 关键依赖：StocktakeRepository.orderRowMapper、StocktakeService.page
 * @state 关键字段：status(0草稿/1已提交/2已完成)、createdAt、updatedAt
 * @risk 高风险修改点：列表字段调整会影响分页查询映射与前端状态展示
 * @link 相关文件：后端/src/main/java/com/wms/backend/stocktake/repository/StocktakeRepository.java、后端/src/main/java/com/wms/backend/stocktake/controller/StocktakeController.java
 */
package com.wms.backend.stocktake.dto;

import java.time.LocalDateTime;

public record StocktakeOrderResponse(
        Long id,
        String stocktakeNo,
        Long warehouseId,
        String warehouseName,
        String scopeType,
        Integer status,
        String remark,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
