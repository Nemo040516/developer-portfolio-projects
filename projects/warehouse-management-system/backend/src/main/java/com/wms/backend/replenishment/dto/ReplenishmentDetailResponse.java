/**
 * @file 速览索引
 * @summary 补货建议详情响应 DTO，聚合补货主表字段与明细列表。
 * @core 1. 返回计划号、仓库信息与状态参数
 * @core 2. 返回 calc/lead/safety 与采购草稿号
 * @core 3. 返回 List<ReplenishmentItemResponse> 明细用于前端可解释展示
 * @entry 先看：planNo、status、calcDays、purchaseDraftNo、items
 * @deps 关键依赖：ReplenishmentService.detail/calculate/recalculate/confirm/toPurchaseDraft
 * @state 关键字段：status(0待确认/1待转采购/2已转草稿)、generatedAt、items
 * @risk 高风险修改点：items 结构或状态口径变化会影响补货看板与流程按钮状态
 * @link 相关文件：后端/src/main/java/com/wms/backend/replenishment/service/ReplenishmentService.java、后端/src/main/java/com/wms/backend/replenishment/dto/ReplenishmentItemResponse.java
 */
package com.wms.backend.replenishment.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * M6 补货建议详情响应（主表 + 明细）。
 */
public record ReplenishmentDetailResponse(
        Long id,
        String planNo,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Integer status,
        Integer calcDays,
        Integer leadTimeDays,
        Integer safetyDays,
        String purchaseDraftNo,
        String remark,
        Long createdBy,
        LocalDateTime generatedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ReplenishmentItemResponse> items
) {
}
