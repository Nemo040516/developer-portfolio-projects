/**
 * @file 速览索引
 * @summary 出库明细请求 DTO，承载每行 SKU+库位+数量并提供数量校验。
 * @core 1. 约束 SKU 与库位必填
 * @core 2. 约束计划数量 planQty 必须大于0
 * @core 3. 允许实出数量 actualQty 为空或非负
 * @entry 先看：skuId、locationId、planQty、actualQty
 * @deps 关键依赖：OutboundCreateRequest/OutboundUpdateRequest、OutboundService.validateMasterData
 * @state 关键字段：planQty(@Min(1))、actualQty(@Min(0))、remark(max=255)
 * @risk 高风险修改点：数量字段规则变更会影响提交/确认有效数量计算与库存扣减逻辑
 * @link 相关文件：后端/src/main/java/com/wms/backend/outbound/service/OutboundService.java、后端/src/main/java/com/wms/backend/outbound/dto/OutboundCreateRequest.java
 */
package com.wms.backend.outbound.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OutboundItemRequest(
        @NotNull(message = "SKU不能为空")
        Long skuId,
        @NotNull(message = "出库库位不能为空")
        Long locationId,
        @NotNull(message = "计划出库数量不能为空")
        @Min(value = 1, message = "计划出库数量必须大于0")
        Integer planQty,
        @Min(value = 0, message = "实出数量不能小于0")
        Integer actualQty,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark
) {
}
