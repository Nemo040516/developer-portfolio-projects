/**
 * @file 速览索引
 * @summary 上架单详情响应 DTO，聚合上架主单信息与明细列表返回前端。
 * @core 1. 返回主单号、来源信息与仓库信息
 * @core 2. 返回状态、备注与审计字段
 * @core 3. 嵌套返回 List<PutawayItemResponse> 明细
 * @entry 先看：putawayNo、status、warehouseName、items
 * @deps 关键依赖：PutawayService.detail、PutawayRepository.findOrderById/listItemsByOrderId
 * @state 关键字段：status(0草稿/1已提交/2已完成)、items
 * @risk 高风险修改点：items 字段结构变化会影响上架详情页与确认流程回显
 * @link 相关文件：后端/src/main/java/com/wms/backend/putaway/service/PutawayService.java、后端/src/main/java/com/wms/backend/putaway/dto/PutawayItemResponse.java
 */
package com.wms.backend.putaway.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PutawayDetailResponse(
        Long id,
        String putawayNo,
        String sourceType,
        Long sourceOrderId,
        String sourceOrderNo,
        Long warehouseId,
        String warehouseName,
        Integer status,
        String remark,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<PutawayItemResponse> items
) {
}
