/**
 * @file 速览索引
 * @summary 上架明细请求 DTO，定义单行 SKU 与目标库位的上架数量参数约束。
 * @core 1. 约束 skuId/locationId/planQty 必填
 * @core 2. 约束 planQty 必须大于0
 * @core 3. 约束 actualQty 非负并允许为空
 * @entry 先看：skuId、locationId、planQty、actualQty
 * @deps 关键依赖：PutawayCreateRequest/PutawayUpdateRequest、PutawayService.create/update
 * @state 关键字段：planQty(@Min(1))、actualQty(@Min(0))
 * @risk 高风险修改点：数量下限规则变更会影响可上架校验与确认落位逻辑
 * @link 相关文件：后端/src/main/java/com/wms/backend/putaway/service/PutawayService.java、后端/src/main/java/com/wms/backend/putaway/dto/PutawayCreateRequest.java
 */
package com.wms.backend.putaway.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PutawayItemRequest(
        @NotNull(message = "SKU不能为空")
        Long skuId,
        @NotNull(message = "目标库位不能为空")
        Long locationId,
        @NotNull(message = "计划上架数量不能为空")
        @Min(value = 1, message = "计划上架数量必须大于0")
        Integer planQty,
        @Min(value = 0, message = "实上数量不能小于0")
        Integer actualQty,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark
) {
}
