/*
 * @file 速览索引
 * @summary 供应商接口文件，负责供应商分页、创建、更新与状态切换请求。
 * @core 1. 供应商分页
 * @core 2. 供应商新增与编辑
 * @core 3. 供应商状态切换
 * @entry 先看：supplierPageApi、supplierCreateApi、supplierUpdateApi、supplierStatusApi
 * @deps 关键依赖：http.js
 * @risk 高风险修改点：联系方式字段、交期字段、状态路径
 * @link 相关文件：前端/src/components/SupplierPanel.vue、后端/src/main/java/com/wms/backend/supplier/controller/SupplierController.java
 */
import http from "./http";

export function supplierPageApi(params) {
  return http.get("/suppliers", { params });
}

export function supplierCreateApi(payload) {
  return http.post("/suppliers", payload);
}

export function supplierUpdateApi(id, payload) {
  return http.put(`/suppliers/${id}`, payload);
}

export function supplierStatusApi(id, status) {
  return http.put(`/suppliers/${id}/status`, { status });
}
