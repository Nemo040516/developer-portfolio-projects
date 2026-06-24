/*
 * @file 速览索引
 * @summary 仓库接口文件，负责仓库分页、创建、更新、状态切换与仓库选项请求。
 * @core 1. 仓库分页
 * @core 2. 仓库新增与编辑
 * @core 3. 仓库状态切换
 * @core 4. 仓库选项获取
 * @entry 先看：warehousePageApi、warehouseCreateApi、warehouseStatusApi、warehouseOptionsApi
 * @deps 关键依赖：http.js
 * @risk 高风险修改点：选项接口被多模块复用、状态路径、表单字段一致性
 * @link 相关文件：前端/src/components/WarehousePanel.vue、后端/src/main/java/com/wms/backend/warehouse/controller/WarehouseController.java
 */
import http from "./http";

export function warehousePageApi(params) {
  return http.get("/warehouses", { params });
}

export function warehouseCreateApi(payload) {
  return http.post("/warehouses", payload);
}

export function warehouseOptionsApi() {
  return http.get("/warehouses/options");
}

export function warehouseUpdateApi(id, payload) {
  return http.put(`/warehouses/${id}`, payload);
}

export function warehouseStatusApi(id, status) {
  return http.put(`/warehouses/${id}/status`, { status });
}
