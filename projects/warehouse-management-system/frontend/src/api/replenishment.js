/*
 * @file 速览索引
 * @summary 补货接口文件，负责补货计划查询、E1统计指标、重算、确认、最终数量调整与转采购草稿请求。
 * @core 1. 补货计划分页与详情
 * @core 2. E1统计指标查询
 * @core 3. 生成与重算建议
 * @core 4. 确认建议与调整最终数量
 * @core 5. 转采购草稿
 * @entry 先看：replenishmentPageApi、replenishmentMetricsApi、replenishmentCalculateApi、replenishmentConfirmApi、replenishmentToPurchaseDraftApi
 * @deps 关键依赖：http.js
 * @risk 高风险修改点：状态流转接口、最终数量接口、首页快捷筛选参数
 * @link 相关文件：前端/src/components/ReplenishmentPanel.vue、后端/src/main/java/com/wms/backend/replenishment/controller/ReplenishmentController.java
 */
import http from "./http";

export function replenishmentPageApi(params) {
  return http.get("/replenishments", { params });
}

export function replenishmentDetailApi(id) {
  return http.get(`/replenishments/${id}`);
}

export function replenishmentMetricsApi(params) {
  return http.get("/replenishments/metrics", { params });
}

export function replenishmentCalculateApi(payload) {
  return http.post("/replenishments/calculate", payload);
}

export function replenishmentRecalculateApi(id, payload) {
  return http.put(`/replenishments/${id}/recalculate`, payload);
}

export function replenishmentConfirmApi(id) {
  return http.put(`/replenishments/${id}/confirm`);
}

export function replenishmentUpdateFinalQtyApi(id, itemId, payload) {
  return http.put(`/replenishments/${id}/items/${itemId}/final-qty`, payload);
}

export function replenishmentToPurchaseDraftApi(id) {
  return http.post(`/replenishments/${id}/to-purchase-draft`);
}
