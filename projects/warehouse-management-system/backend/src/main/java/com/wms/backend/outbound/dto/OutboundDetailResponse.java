/**
 * @file 速览索引
 * @summary 出库单详情响应 DTO，返回主单信息与明细列表用于详情页展示。
 * @core 1. 返回出库单主字段与状态信息
 * @core 2. 返回仓库与目标信息
 * @core 3. 返回明细集合 items 供前端渲染
 * @entry 先看：outboundNo、status、warehouseName、items
 * @deps 关键依赖：OutboundService.detail、OutboundRepository.findOrderById/listItemsByOrderId
 * @state 关键字段：createdBy、createdAt、updatedAt、List<OutboundItemResponse> items
 * @risk 高风险修改点：主单字段与明细结构变更会联动详情接口与前端详情弹窗
 * @link 相关文件：后端/src/main/java/com/wms/backend/outbound/service/OutboundService.java、后端/src/main/java/com/wms/backend/outbound/dto/OutboundItemResponse.java
 */
package com.wms.backend.outbound.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OutboundDetailResponse(
        Long id,
        String outboundNo,
        String outboundType,
        String targetName,
        Long warehouseId,
        String warehouseName,
        Integer status,
        String remark,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<OutboundItemResponse> items
) {
}
