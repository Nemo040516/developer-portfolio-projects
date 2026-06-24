/**
 * @file 速览索引
 * @summary 入库单列表响应DTO，提供分页列表所需主单信息（不含明细集合）。
 * @core 1. 返回入库单标识与单号
 * @core 2. 返回供应商、仓库与状态
 * @core 3. 返回创建人及时间审计字段
 * @entry 先看：inboundNo、supplierName、warehouseName、status
 * @deps 关键依赖：InboundService 分页接口、前端入库列表页
 * @risk 高风险修改点：状态字段口径与前端状态标签映射
 * @link 相关文件：后端/src/main/java/com/wms/backend/inbound/dto/InboundDetailResponse.java
 */
package com.wms.backend.inbound.dto;

import java.time.LocalDateTime;

public record InboundOrderResponse(
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
        LocalDateTime updatedAt
) {
}
