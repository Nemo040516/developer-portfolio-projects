/**
 * @file 速览索引
 * @summary 入库单编辑请求 DTO，承载主单字段与入库明细的校验入参。
 * @core 1. 承载供应商、仓库、备注等主单编辑字段
 * @core 2. 约束明细 items 非空并触发明细级联校验
 * @core 3. 统一入库单更新接口的请求结构
 * @entry 先看：supplierId、warehouseId、items
 * @deps 关键依赖：InboundController.update、InboundService.update、InboundItemRequest
 * @state 关键字段：items(List<@Valid InboundItemRequest>)、remark(max=255)
 * @risk 高风险修改点：字段命名与校验注解变更会联动前端表单与服务层校验
 * @link 相关文件：后端/src/main/java/com/wms/backend/inbound/controller/InboundController.java、后端/src/main/java/com/wms/backend/inbound/service/InboundService.java
 */
package com.wms.backend.inbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InboundUpdateRequest(
        @NotNull(message = "供应商不能为空")
        Long supplierId,
        @NotNull(message = "仓库不能为空")
        Long warehouseId,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark,
        @NotEmpty(message = "入库明细不能为空")
        List<@Valid InboundItemRequest> items
) {
}
