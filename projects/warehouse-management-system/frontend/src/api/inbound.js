/*
 * @file 速览索引
 * @summary 入库接口文件，负责入库单分页、详情、创建、编辑、提交与确认请求。
 * @core 1. 入库单分页与详情
 * @core 2. 入库单新增与编辑
 * @core 3. 入库单提交与确认
 * @entry 先看：inboundPageApi、inboundDetailApi、inboundCreateApi、inboundSubmitApi、inboundConfirmApi
 * @deps 关键依赖：http.js
 * @risk 高风险修改点：状态流转路径、详情接口结构、编辑与确认接口区分
 * @link 相关文件：前端/src/components/InboundPanel.vue、后端/src/main/java/com/wms/backend/inbound/controller/InboundController.java
 */
import http from "./http";

export function inboundPageApi(params) {
  return http.get("/inbounds", { params });
}

export function inboundDetailApi(id) {
  return http.get(`/inbounds/${id}`);
}

export function inboundCreateApi(payload) {
  return http.post("/inbounds", payload);
}

export function inboundUpdateApi(id, payload) {
  return http.put(`/inbounds/${id}`, payload);
}

export function inboundDeleteApi(id) {
  return http.delete(`/inbounds/${id}`);
}

export function inboundSubmitApi(id) {
  return http.put(`/inbounds/${id}/submit`);
}

export function inboundConfirmApi(id) {
  return http.put(`/inbounds/${id}/confirm`);
}
