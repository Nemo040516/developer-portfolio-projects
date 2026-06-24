/**
 * @file 速览索引
 * @summary 仓库下拉选项 DTO，提供业务表单可选择的仓库主键与名称信息。
 * @core 1. 返回仓库主键 id
 * @core 2. 返回仓库编码 warehouseCode
 * @core 3. 返回仓库名称 warehouseName
 * @entry 先看：id、warehouseCode、warehouseName
 * @deps 关键依赖：WarehouseController.options、WarehouseService.enabledOptions、WarehouseRepository.listEnabledOptions
 * @state 关键字段：仅保留展示与选择所需三元字段
 * @risk 高风险修改点：字段结构变更会影响前端下拉回显与表单提交映射
 * @link 相关文件：后端/src/main/java/com/wms/backend/warehouse/repository/WarehouseRepository.java、后端/src/main/java/com/wms/backend/warehouse/controller/WarehouseController.java
 */
package com.wms.backend.warehouse.dto;

public record WarehouseOptionResponse(
        Long id,
        String warehouseCode,
        String warehouseName
) {
}
