/**
 * @file 速览索引
 * @summary 仓库响应 DTO，返回仓库基础信息、状态与审计时间字段。
 * @core 1. 返回仓库编码、名称与地址负责人信息
 * @core 2. 返回状态与备注用于治理页展示
 * @core 3. 返回创建/更新时间用于列表审计
 * @entry 先看：warehouseCode、warehouseName、status、createdAt
 * @deps 关键依赖：WarehouseRepository.warehouseRowMapper、WarehouseService.page/create/update/updateStatus
 * @state 关键字段：status(0停用/1启用)、contactPhone、createdAt、updatedAt
 * @risk 高风险修改点：字段口径变更会影响仓库列表展示与上下游模块仓库信息读取
 * @link 相关文件：后端/src/main/java/com/wms/backend/warehouse/repository/WarehouseRepository.java、后端/src/main/java/com/wms/backend/warehouse/controller/WarehouseController.java
 */
package com.wms.backend.warehouse.dto;

import java.time.LocalDateTime;

public record WarehouseResponse(
        Long id,
        String warehouseCode,
        String warehouseName,
        String address,
        String managerName,
        String contactPhone,
        Integer status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
