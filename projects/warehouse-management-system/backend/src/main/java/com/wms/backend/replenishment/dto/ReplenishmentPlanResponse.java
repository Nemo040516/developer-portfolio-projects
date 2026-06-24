/**
 * @file 速览索引
 * @summary 补货计划主表响应 DTO，用于补货计划分页与详情主单信息返回。
 * @core 1. 返回计划编号、仓库信息与状态
 * @core 2. 返回计算参数（calcDays/leadTimeDays/safetyDays）
 * @core 3. 返回采购草稿号与审计时间字段
 * @entry 先看：planNo、status、calcDays、purchaseDraftNo、generatedAt
 * @deps 关键依赖：ReplenishmentRepository.planRowMapper、ReplenishmentService.page/detail
 * @state 关键字段：status(0待确认/1待转采购/2已生成采购草稿)、purchaseDraftNo
 * @risk 高风险修改点：字段口径变更会联动计划列表、详情页与状态流转展示
 * @link 相关文件：后端/src/main/java/com/wms/backend/replenishment/service/ReplenishmentService.java、后端/src/main/java/com/wms/backend/replenishment/controller/ReplenishmentController.java
 */
package com.wms.backend.replenishment.dto;

import java.time.LocalDateTime;

public record ReplenishmentPlanResponse(
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
        LocalDateTime updatedAt
) {
}
