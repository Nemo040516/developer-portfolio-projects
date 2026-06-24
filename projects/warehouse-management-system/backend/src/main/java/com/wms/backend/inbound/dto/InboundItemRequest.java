/**
 * @file 速览索引
 * @summary 入库明细请求DTO，定义单条入库明细的SKU与数量校验规则。
 * @core 1. 校验 SKU 必填
 * @core 2. 校验计划数量必须 >= 1
 * @core 3. 校验实收数量必须 >= 0
 * @entry 先看：skuId、planQty、receivedQty
 * @deps 关键依赖：InboundCreateRequest、InboundUpdateRequest
 * @risk 高风险修改点：数量最小值约束（影响库存变更边界）
 * @link 相关文件：后端/src/main/java/com/wms/backend/inbound/dto/InboundCreateRequest.java
 */
package com.wms.backend.inbound.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InboundItemRequest(
        @NotNull(message = "SKU不能为空")
        Long skuId,
        @NotNull(message = "计划数量不能为空")
        @Min(value = 1, message = "计划数量必须大于0")
        Integer planQty,
        @Min(value = 0, message = "实收数量不能小于0")
        Integer receivedQty,
        String remark
) {
}
