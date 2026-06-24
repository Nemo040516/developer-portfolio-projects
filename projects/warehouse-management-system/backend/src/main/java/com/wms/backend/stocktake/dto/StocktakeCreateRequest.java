/**
 * @file 速览索引
 * @summary 盘点单新增请求 DTO，承载主单字段与盘点明细并执行入参校验。
 * @core 1. 承载仓库、盘点范围、备注等主单字段
 * @core 2. 约束盘点明细 items 非空
 * @core 3. 通过 List<@Valid StocktakeItemRequest> 触发明细级联校验
 * @entry 先看：warehouseId、scopeType、items
 * @deps 关键依赖：StocktakeController.create、StocktakeService.create、StocktakeItemRequest
 * @state 关键字段：scopeType(max=32)、remark(max=255)、items(@NotEmpty)
 * @risk 高风险修改点：字段或校验变更会联动盘点建单表单与服务层主数据校验
 * @link 相关文件：后端/src/main/java/com/wms/backend/stocktake/service/StocktakeService.java、后端/src/main/java/com/wms/backend/stocktake/controller/StocktakeController.java
 */
package com.wms.backend.stocktake.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StocktakeCreateRequest(
        @NotNull(message = "仓库不能为空")
        Long warehouseId,
        @Size(max = 32, message = "盘点范围类型长度不能超过32")
        String scopeType,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark,
        @NotEmpty(message = "盘点明细不能为空")
        List<@Valid StocktakeItemRequest> items
) {
}
