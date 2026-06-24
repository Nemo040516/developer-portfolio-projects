/*
 * @file 速览索引
 * @summary 上架接口文件，负责上架单分页、详情、创建、编辑、提交与确认请求。
 * @core 1. 上架单分页与详情
 * @core 2. 上架单新增与编辑
 * @core 3. 上架单提交与确认
 * @entry 先看：putawayPageApi、putawayDetailApi、putawayCreateApi、putawaySubmitApi、putawayConfirmApi
 * @deps 关键依赖：http.js
 * @risk 高风险修改点：状态流转路径、详情结构、确认上架接口
 * @link 相关文件：前端/src/components/PutawayPanel.vue、后端/src/main/java/com/wms/backend/putaway/controller/PutawayController.java
 */
import http from "./http";

export function putawayPageApi(params) {
  return http.get("/putaways", { params });
}

export function putawayDetailApi(id) {
  return http.get(`/putaways/${id}`);
}

export function putawayCreateApi(payload) {
  return http.post("/putaways", payload);
}

export function putawayUpdateApi(id, payload) {
  return http.put(`/putaways/${id}`, payload);
}

export function putawayDeleteApi(id) {
  return http.delete(`/putaways/${id}`);
}

export function putawaySubmitApi(id) {
  return http.put(`/putaways/${id}/submit`);
}

export function putawayConfirmApi(id) {
  return http.put(`/putaways/${id}/confirm`);
}
