/*
 * @file 速览索引
 * @summary 用户接口文件，负责用户分页、新增、状态切换与密码重置请求。
 * @core 1. 用户分页
 * @core 2. 用户新增
 * @core 3. 用户状态切换
 * @core 4. 密码重置
 * @entry 先看：userPageApi、userCreateApi、userStatusApi、userResetPasswordApi
 * @deps 关键依赖：http.js
 * @risk 高风险修改点：分页筛选参数、状态接口路径、密码重置路径
 * @link 相关文件：前端/src/components/UserPanel.vue、后端/src/main/java/com/wms/backend/user/controller/UserController.java
 */
import http from "./http";

export function userPageApi(params) {
  return http.get("/users", { params });
}

export function userCreateApi(payload) {
  return http.post("/users", payload);
}

export function userStatusApi(id, status) {
  return http.put(`/users/${id}/status`, { status });
}

export function userResetPasswordApi(id) {
  return http.put(`/users/${id}/reset-password`);
}
