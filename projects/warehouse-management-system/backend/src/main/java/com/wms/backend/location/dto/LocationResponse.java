/**
 * @file 速览索引
 * @summary 库位分页/详情响应 DTO，返回库位主数据及审计信息。
 * @core 1. 返回库位主键、仓库归属与编码信息
 * @core 2. 返回库区、类型、容量、状态与备注
 * @core 3. 返回创建与更新时间用于追溯
 * @entry 先看：warehouseCode、locationCode、locationType、capacity、status
 * @deps 关键依赖：LocationRepository.locationRowMapper、LocationService.page/create/update
 * @state 关键字段：status、capacity、createdAt、updatedAt
 * @risk 高风险修改点：字段调整会影响库位列表展示、状态更新回显与 SQL 映射
 * @link 相关文件：后端/src/main/java/com/wms/backend/location/repository/LocationRepository.java、后端/src/main/java/com/wms/backend/location/controller/LocationController.java
 */
package com.wms.backend.location.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LocationResponse(
        Long id,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        String locationCode,
        String areaName,
        String locationType,
        BigDecimal capacity,
        Integer status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
