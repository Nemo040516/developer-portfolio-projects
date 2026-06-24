/**
 * @file 速览索引
 * @summary 入库单详情响应DTO，聚合入库主单信息与明细列表用于详情页展示。
 * @core 1. 返回入库单主字段（单号、供应商、仓库、状态）
 * @core 2. 返回审计字段（创建人、创建时间、更新时间）
 * @core 3. 返回明细集合 `items`
 * @entry 先看：id、inboundNo、status、items
 * @deps 关键依赖：InboundService、InboundItemResponse
 * @risk 高风险修改点：字段命名与前端详情页绑定一致性
 * @link 相关文件：后端/src/main/java/com/wms/backend/inbound/dto/InboundItemResponse.java
 */
package com.wms.backend.inbound.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InboundDetailResponse(
        Long id,
        String inboundNo,
        Long supplierId,
        String supplierName,
        Long warehouseId,
        String warehouseName,
        Integer status,
        String remark,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<InboundItemResponse> items
) {
}
