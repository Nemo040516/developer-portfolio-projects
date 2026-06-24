/**
 * @file 速览索引
 * @summary 补货统计总响应DTO，聚合E1看板的时间范围、仓库信息、概览指标与Top SKU。
 * @core 1. 描述统计范围（开始/结束日期）
 * @core 2. 描述统计对象（全仓或指定仓库）
 * @core 3. 输出概览指标与Top SKU列表
 * @entry 先看：overview、topAdjustSkus
 * @deps 关键依赖：ReplenishmentMetricsOverviewResponse、ReplenishmentTopAdjustSkuResponse
 * @risk 高风险修改点：字段命名变更会影响前端看板与导出逻辑
 * @link 相关文件：后端/src/main/java/com/wms/backend/replenishment/controller/ReplenishmentController.java
 */
package com.wms.backend.replenishment.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * E1 补货统计接口响应体。
 */
public record ReplenishmentMetricsResponse(
        LocalDate startDate,
        LocalDate endDate,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        ReplenishmentMetricsOverviewResponse overview,
        List<ReplenishmentTopAdjustSkuResponse> topAdjustSkus
) {
}
