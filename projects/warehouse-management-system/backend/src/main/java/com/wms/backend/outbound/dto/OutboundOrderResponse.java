/**
 * @file 速览索引
 * @summary 出库单列表响应 DTO，承载出库主单摘要字段供分页与详情头部展示。
 * @core 1. 返回单号、类型、目标方与仓库信息
 * @core 2. 返回状态字段支持状态机展示
 * @core 3. 返回创建人与审计时间字段
 * @entry 先看：outboundNo、status、warehouseName、createdAt
 * @deps 关键依赖：OutboundRepository.orderRowMapper、OutboundService.page/detail
 * @state 关键字段：status(0草稿/1已提交/2已完成)、createdBy、updatedAt
 * @risk 高风险修改点：字段改名会影响列表渲染、状态筛选与详情拼装
 * @link 相关文件：后端/src/main/java/com/wms/backend/outbound/repository/OutboundRepository.java、后端/src/main/java/com/wms/backend/outbound/service/OutboundService.java
 */
package com.wms.backend.outbound.dto;

import java.time.LocalDateTime;

public record OutboundOrderResponse(
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
        LocalDateTime updatedAt
) {
}
