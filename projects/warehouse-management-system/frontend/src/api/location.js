/*
 * @file 速览索引
 * @summary 库位接口文件，负责库位分页、创建、更新与状态切换请求。
 * @core 1. 库位分页
 * @core 2. 库位新增与编辑
 * @core 3. 库位状态切换
 * @entry 先看：locationPageApi、locationCreateApi、locationUpdateApi、locationStatusApi
 * @deps 关键依赖：http.js
 * @risk 高风险修改点：仓库绑定字段、状态路径、容量/类型字段一致性
 * @link 相关文件：前端/src/components/LocationPanel.vue、后端/src/main/java/com/wms/backend/location/controller/LocationController.java
 */
import http from "./http";

export function locationPageApi(params) {
  return http.get("/locations", { params });
}

export function locationCreateApi(payload) {
  return http.post("/locations", payload);
}

export function locationUpdateApi(id, payload) {
  return http.put(`/locations/${id}`, payload);
}

export function locationStatusApi(id, status) {
  return http.put(`/locations/${id}/status`, { status });
}
