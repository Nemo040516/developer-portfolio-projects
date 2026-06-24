/**
 * @file 速览索引
 * @summary 出库单编辑请求 DTO，约束出库主表与明细更新参数。
 * @core 1. 约束 warehouseId 必填
 * @core 2. 约束 outboundType/targetName/remark 文本长度
 * @core 3. 约束 items 非空并级联校验 OutboundItemRequest
 * @entry 先看：warehouseId、outboundType、items
 * @deps 关键依赖：OutboundController.update、OutboundService.update、OutboundItemRequest
 * @state 关键字段：items(@NotEmpty List<@Valid OutboundItemRequest>)
 * @risk 高风险修改点：明细必填或字段长度规则变更会影响编辑接口兼容性
 * @link 相关文件：后端/src/main/java/com/wms/backend/outbound/service/OutboundService.java、后端/src/main/java/com/wms/backend/outbound/controller/OutboundController.java
 */
package com.wms.backend.outbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OutboundUpdateRequest(
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
