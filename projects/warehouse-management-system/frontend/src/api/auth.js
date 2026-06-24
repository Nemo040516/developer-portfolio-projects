/*
 * @file 速览索引
 * @summary 鉴权接口文件，负责登录与当前用户信息获取。
 * @core 1. 登录
 * @core 2. 获取当前用户与菜单
 * @entry 先看：loginApi、meApi
 * @deps 关键依赖：http.js
 * @risk 高风险修改点：登录返回结构、当前用户菜单结构、token 使用方式
 * @link 相关文件：前端/src/App.vue、后端/src/main/java/com/wms/backend/auth/controller/AuthController.java
 */
import http from "./http";

export function loginApi(payload) {
  return http.post("/auth/login", payload);
}

export function meApi() {
  return http.get("/auth/me");
}
