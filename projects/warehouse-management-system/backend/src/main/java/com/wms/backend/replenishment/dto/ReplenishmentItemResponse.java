/**
 * @file 速览索引
 * @summary 补货建议明细响应 DTO，返回库存现状、预测结果与建议/最终量。
 * @core 1. 返回 skuCode/skuName 与 currentQty/safeQty
 * @core 2. 返回 predictedDailySales/predictedTotalQty/shortageQty
 * @core 3. 返回 suggestedQty/finalQty 与推荐来源置信度
 * @entry 先看：skuCode、currentQty、suggestedQty、finalQty、confidence
 * @deps 关键依赖：ReplenishmentRepository.itemRowMapper、ReplenishmentService.detail/rebuildItems
 * @state 关键字段：suggestedQty、finalQty、recoSource、confidence
 * @risk 高风险修改点：建议量与最终量字段口径变化会影响采纳率与人工干预率统计
 * @link 相关文件：后端/src/main/java/com/wms/backend/replenishment/repository/ReplenishmentRepository.java、后端/src/main/java/com/wms/backend/replenishment/dto/ReplenishmentDetailResponse.java
 */
package com.wms.backend.replenishment.dto;

import java.math.BigDecimal;

/**
 * M6 补货建议明细响应。
 */
public record ReplenishmentItemResponse(
        Long id,
        Long planId,
        Long skuId,
        String skuCode,
        String skuName,
        Integer currentQty,
        Integer safeQty,
        BigDecimal predictedDailySales,
        Integer predictedTotalQty,
        Integer shortageQty,
        Integer suggestedQty,
        Integer finalQty,
        String recoSource,
        BigDecimal confidence,
        String reason
) {
}
