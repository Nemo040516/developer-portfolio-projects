/**
 * @file 速览索引
 * @summary 补货统计概览DTO，承载E1看板核心指标（采纳率/干预率/命中率/MAPE/周转率）。
 * @core 1. 汇总计划与明细总量
 * @core 2. 提供比率型指标给前端看板
 * @entry 先看：adoptionRate、manualAdjustRate、shortageHitRate、inventoryTurnoverRate
 * @deps 关键依赖：ReplenishmentMetricsResponse
 * @risk 高风险修改点：字段口径与技术手册评估口径需保持一致
 * @link 相关文件：后端/src/main/java/com/wms/backend/replenishment/service/ReplenishmentService.java
 */
package com.wms.backend.replenishment.dto;

import java.math.BigDecimal;

/**
 * E1 指标概览响应：
 * - 所有比率均使用 0~1 小数表达，前端按百分比展示。
 */
public record ReplenishmentMetricsOverviewResponse(
        Long planCount,
        Long itemCount,
        BigDecimal adoptionRate,
        BigDecimal manualAdjustRate,
        BigDecimal shortageHitRate,
        BigDecimal mape,
        BigDecimal inventoryTurnoverRate
) {
}
