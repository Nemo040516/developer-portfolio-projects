/**
 * @file 速览索引
 * @summary 入库单创建请求DTO，定义创建入库单所需主表字段与明细列表校验。
 * @core 1. 校验供应商与仓库必填
 * @core 2. 校验备注长度上限
 * @core 3. 校验明细列表非空并级联校验明细项
 * @entry 先看：supplierId、warehouseId、items
 * @deps 关键依赖：InboundController、InboundService、InboundItemRequest
 * @risk 高风险修改点：明细非空与级联校验注解（影响创建入库单门禁）
 * @link 相关文件：后端/src/main/java/com/wms/backend/inbound/dto/InboundItemRequest.java
 */
package com.wms.backend.inbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InboundCreateRequest(
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
