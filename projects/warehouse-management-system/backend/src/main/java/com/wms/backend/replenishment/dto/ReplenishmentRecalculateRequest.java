/**
 * @file 速览索引
 * @summary 补货计划重算请求 DTO，承载可选调参字段并支持“原参数重算”。
 * @core 1. calcDays/leadTimeDays/safetyDays 允许为空，空值沿用当前计划参数
 * @core 2. remark 可选，传空字符串会在服务层归一化为 null
 * @core 3. 支持局部调参后重建补货明细
 * @entry 先看：calcDays、leadTimeDays、safetyDays、remark
 * @deps 关键依赖：ReplenishmentController.recalculate、ReplenishmentService.recalculate/resolveRecalculateRemark
 * @state 关键规则：字段全可选，请求体可为空（Controller 用 required=false）
 * @risk 高风险修改点：字段语义变更会影响重算兼容性与备注清空行为
 * @link 相关文件：后端/src/main/java/com/wms/backend/replenishment/service/ReplenishmentService.java、后端/src/main/java/com/wms/backend/replenishment/controller/ReplenishmentController.java
 */
package com.wms.backend.replenishment.dto;

public record ReplenishmentRecalculateRequest(
        Integer calcDays,
        Integer leadTimeDays,
        Integer safetyDays,
        String remark
) {
}
