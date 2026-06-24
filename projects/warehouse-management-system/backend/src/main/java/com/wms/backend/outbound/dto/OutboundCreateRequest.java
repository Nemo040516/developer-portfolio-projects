/**
 * @file 速览索引
 * @summary 出库单新增请求 DTO，承载出库主单字段与明细列表并执行入参校验。
 * @core 1. 承载仓库、出库类型、目标名称、备注等主单字段
 * @core 2. 约束出库明细 items 非空
 * @core 3. 对明细执行级联校验 List<@Valid OutboundItemRequest>
 * @entry 先看：warehouseId、outboundType、targetName、items
 * @deps 关键依赖：OutboundController.create、OutboundService.create、OutboundItemRequest
 * @state 关键字段：outboundType(max=32)、targetName(max=128)、remark(max=255)
 * @risk 高风险修改点：字段名与校验规则变更会影响前端新增表单与服务层主数据校验
 * @link 相关文件：后端/src/main/java/com/wms/backend/outbound/service/OutboundService.java、后端/src/main/java/com/wms/backend/outbound/controller/OutboundController.java
 */
package com.wms.backend.outbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OutboundCreateRequest(
        @NotNull(message = "仓库不能为空")
        Long warehouseId,
        @Size(max = 32, message = "出库类型长度不能超过32")
        String outboundType,
        @Size(max = 128, message = "目标名称长度不能超过128")
        String targetName,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark,
        @NotEmpty(message = "出库明细不能为空")
        List<@Valid OutboundItemRequest> items
) {
}
