/*
 * @file 速览索引
 * @summary 角色接口文件，负责角色分页、角色选项与角色状态切换请求。
 * @core 1. 角色分页
 * @core 2. 角色选项
 * @core 3. 角色状态切换
 * @entry 先看：rolePageApi、roleOptionsApi、roleStatusApi
 * @deps 关键依赖：http.js
 * @risk 高风险修改点：角色新增接口已删除、状态路径、角色选项口径
 * @link 相关文件：前端/src/components/UserPanel.vue、后端/src/main/java/com/wms/backend/role/controller/RoleController.java
 */
import http from "./http";

export function rolePageApi(params) {
  return http.get("/roles", { params });
}

export function roleOptionsApi() {
  return http.get("/roles/options");
}

export function roleStatusApi(id, status) {
  return http.put(`/roles/${id}/status`, { status });
}
