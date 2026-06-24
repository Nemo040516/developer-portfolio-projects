/*
 * @file 速览索引
 * @summary 盘点接口文件，负责盘点单分页、详情、创建、编辑、提交、确认与账面库存查询请求。
 * @core 1. 盘点单分页与详情
 * @core 2. 盘点单新增与编辑
 * @core 3. 盘点单提交与确认
 * @core 4. 账面库存查询
 * @entry 先看：stocktakePageApi、stocktakeDetailApi、stocktakeCreateApi、stocktakeConfirmApi、stocktakeBookStocksApi
 * @deps 关键依赖：http.js
 * @risk 高风险修改点：账面库存接口、状态流转路径、差异字段口径
 * @link 相关文件：前端/src/components/StocktakePanel.vue、后端/src/main/java/com/wms/backend/stocktake/controller/StocktakeController.java
 */
import http from "./http";

export function stocktakePageApi(params) {
  return http.get("/stocktakes", { params });
}

export function stocktakeDetailApi(id) {
  return http.get(`/stocktakes/${id}`);
}

export function stocktakeCreateApi(payload) {
  return http.post("/stocktakes", payload);
}

export function stocktakeUpdateApi(id, payload) {
  return http.put(`/stocktakes/${id}`, payload);
}

export function stocktakeDeleteApi(id) {
  return http.delete(`/stocktakes/${id}`);
}

export function stocktakeSubmitApi(id) {
  return http.put(`/stocktakes/${id}/submit`);
}

export function stocktakeConfirmApi(id) {
  return http.put(`/stocktakes/${id}/confirm`);
}

export function stocktakeBookStocksApi(params) {
  return http.get("/stocktakes/book-stocks", { params });
}
