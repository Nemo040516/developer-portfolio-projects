/**
 * @file 速览索引
 * @summary 上架单创建请求 DTO，承载上架主单基础参数与明细列表。
 * @core 1. 约束 warehouseId 必填
 * @core 2. 约束 sourceType/sourceOrderNo/remark 长度
 * @core 3. 约束 items 非空并级联校验 PutawayItemRequest
 * @entry 先看：warehouseId、sourceType、sourceOrderNo、items
 * @deps 关键依赖：PutawayController.create、PutawayService.create、PutawayItemRequest
 * @state 关键字段：sourceType(默认 INBOUND)、items(@NotEmpty List<@Valid PutawayItemRequest>)
 * @risk 高风险修改点：来源字段与明细约束变更会影响上架创建与后续追溯
 * @link 相关文件：后端/src/main/java/com/wms/backend/putaway/service/PutawayService.java、后端/src/main/java/com/wms/backend/putaway/controller/PutawayController.java
 */
package com.wms.backend.putaway.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PutawayCreateRequest(
        @NotNull(message = "仓库不能为空")
        Long warehouseId,
        @Size(max = 32, message = "来源类型长度不能超过32")
        String sourceType,
        Long sourceOrderId,
        @Size(max = 64, message = "来源单号长度不能超过64")
        String sourceOrderNo,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark,
        @NotEmpty(message = "上架明细不能为空")
        List<@Valid PutawayItemRequest> items
) {
}
