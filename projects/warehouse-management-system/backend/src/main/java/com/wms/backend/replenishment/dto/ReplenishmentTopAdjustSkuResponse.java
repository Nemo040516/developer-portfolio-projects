/**
 * @file 速览索引
 * @summary 人工干预Top SKU DTO，用于展示“系统建议与人工改量偏差最大的SKU”。
 * @core 1. 提供SKU基础标识
 * @core 2. 提供偏差总量与干预次数
 * @entry 先看：adjustAbsQtyTotal、adjustItemCount
 * @deps 关键依赖：ReplenishmentMetricsResponse
 * @risk 高风险修改点：排序口径变更需同步更新前端与文档
 * @link 相关文件：前端/src/components/ReplenishmentPanel.vue
 */
package com.wms.backend.replenishment.dto;

/**
 * E1 指标“人工干预 Top SKU”响应行。
 */
public record ReplenishmentTopAdjustSkuResponse(
        Long skuId,
        String skuCode,
        String skuName,
        Integer adjustAbsQtyTotal,
        Integer adjustItemCount
) {
}
