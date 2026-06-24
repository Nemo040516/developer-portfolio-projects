/*
 * @file 速览索引
 * @summary 出库接口文件，负责出库单分页、详情、创建、编辑、提交、确认与可用库存查询请求。
 * @core 1. 出库单分页与详情
 * @core 2. 出库单新增与编辑
 * @core 3. 出库单提交与确认
 * @core 4. 可用库存查询
 * @entry 先看：outboundPageApi、outboundDetailApi、outboundCreateApi、outboundConfirmApi、outboundAvailableStocksApi
 * @deps 关键依赖：http.js
 * @risk 高风险修改点：可用库存接口、状态流转路径、详情与确认接口
 * @link 相关文件：前端/src/components/OutboundPanel.vue、后端/src/main/java/com/wms/backend/outbound/controller/OutboundController.java
 */
import http from "./http";

export function outboundPageApi(params) {
  return http.get("/outbounds", { params });
}

export function outboundDetailApi(id) {
  return http.get(`/outbounds/${id}`);
}

export function outboundCreateApi(payload) {
  return http.post("/outbounds", payload);
}

export function outboundUpdateApi(id, payload) {
  return http.put(`/outbounds/${id}`, payload);
}

export function outboundDeleteApi(id) {
  return http.delete(`/outbounds/${id}`);
}

export function outboundSubmitApi(id) {
  return http.put(`/outbounds/${id}/submit`);
}

export function outboundConfirmApi(id) {
  return http.put(`/outbounds/${id}/confirm`);
}

export function outboundAvailableStocksApi(params) {
  return http.get("/outbounds/available-stocks", { params });
}
