/*
 * @file 速览索引
 * @summary 库存接口文件，负责库存台账、库存流水、库位库存、预警规则与预警分页请求。
 * @core 1. 库存台账查询
 * @core 2. 库存流水查询
 * @core 3. 库位库存与库位流水查询
 * @core 4. 预警规则与预警数据请求
 * @entry 先看：inventoryStockPageApi、inventoryTxnPageApi、inventoryAlertPageApi、inventoryAlertRulePageApi
 * @deps 关键依赖：http.js
 * @risk 高风险修改点：筛选参数命名、预警规则接口、库存多视图查询口径
 * @link 相关文件：前端/src/components/InventoryPanel.vue、前端/src/components/InventoryAlertPanel.vue
 */
import http from "./http";

export function inventoryStockPageApi(params) {
  return http.get("/inventory/stocks", { params });
}

export function inventoryTxnPageApi(params) {
  return http.get("/inventory/txns", { params });
}

export function inventoryLocationStockPageApi(params) {
  return http.get("/inventory/location-stocks", { params });
}

export function inventoryLocationTxnPageApi(params) {
  return http.get("/inventory/location-txns", { params });
}

export function inventoryAlertRulePageApi(params) {
  return http.get("/inventory/alert-rules", { params });
}

export function inventoryAlertRuleCreateApi(payload) {
  return http.post("/inventory/alert-rules", payload);
}

export function inventoryAlertRuleUpdateApi(id, payload) {
  return http.put(`/inventory/alert-rules/${id}`, payload);
}

export function inventoryAlertPageApi(params) {
  return http.get("/inventory/alerts", { params });
}
