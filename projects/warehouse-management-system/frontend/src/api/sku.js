/*
 * @file 速览索引
 * @summary SKU 接口文件，负责商品分页、创建、更新与状态切换请求。
 * @core 1. SKU 分页
 * @core 2. SKU 新增与编辑
 * @core 3. SKU 状态切换
 * @entry 先看：skuPageApi、skuCreateApi、skuUpdateApi、skuStatusApi
 * @deps 关键依赖：http.js
 * @risk 高风险修改点：SKU 状态路径、字段命名、共享主数据被多模块依赖
 * @link 相关文件：前端/src/components/SkuPanel.vue、后端/src/main/java/com/wms/backend/sku/controller/SkuController.java
 */
import http from "./http";

export function skuPageApi(params) {
  return http.get("/skus", { params });
}

export function skuCreateApi(payload) {
  return http.post("/skus", payload);
}

export function skuUpdateApi(id, payload) {
  return http.put(`/skus/${id}`, payload);
}

export function skuStatusApi(id, status) {
  return http.put(`/skus/${id}/status`, { status });
}
