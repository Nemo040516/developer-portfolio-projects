/*
 * @file 速览索引
 * @summary 预警规则测试数据工具，负责“确保存在可命中预警的启用规则”，供 M5 规则/预警联动用例复用。
 * @core 1. 读取当前登录会话 token
 * @core 2. 选择可用仓库与启用 SKU
 * @core 3. 自动识别规则是否已存在（存在则更新，不存在则新增）
 * @core 4. 返回仓库与 SKU 标识，供 UI 层精准断言
 * @entry 先看：ensureInventoryAlertRuleByApi
 * @deps 依赖：@playwright/test、warehouse/sku/inventory-alert 接口
 * @state 关键数据：warehouseId、skuId、ruleId、warehouseName、skuCode
 * @risk 高风险修改点：规则接口返回结构、分页字段、阈值口径
 * @link 相关文件：前端/e2e/tests/m1-m6/m5-inventory-alert-rule-upsert.spec.ts
 */
import { expect, type APIResponse, type Page } from "@playwright/test";

type ApiEnvelope<T> = {
  code: number;
  message: string;
  data: T;
};

type PagedRecords<T> = {
  total: number;
  records: T[];
};

type WarehouseOption = {
  id: number;
  warehouseCode: string;
  warehouseName: string;
};

type SkuRecord = {
  id: number;
  skuCode: string;
  skuName: string;
  status: number;
};

type AlertRuleRecord = {
  id: number;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  skuId: number;
  skuCode: string;
  skuName: string;
  minQty: number;
  safeQty: number;
  maxQty: number;
  status: number;
  remark: string;
};

export type AlertRuleAnchor = {
  ruleId: number;
  warehouseId: number;
  warehouseName: string;
  skuId: number;
  skuCode: string;
};

// 从会话读取 token，确保 API 前置与 UI 场景共用同一账号上下文。
async function readSessionToken(page: Page): Promise<string> {
  const token = await page.evaluate(() => sessionStorage.getItem("wms_token"));
  expect(token).toBeTruthy();
  return String(token);
}

// 统一解析后端 ApiResponse 包装，减少重复样板代码。
async function parseSuccessEnvelope<T>(response: APIResponse): Promise<T> {
  expect(response.ok()).toBeTruthy();
  const envelope = (await response.json()) as ApiEnvelope<T>;
  expect(envelope.code).toBe(0);
  return envelope.data;
}

// 拉取可用仓库选项，作为规则的 warehouse 维度来源。
async function fetchWarehouseOptions(page: Page, authToken: string): Promise<WarehouseOption[]> {
  const response = await page.request.get("/api/warehouses/options", {
    headers: { Authorization: `Bearer ${authToken}` }
  });
  return parseSuccessEnvelope<WarehouseOption[]>(response);
}

// 拉取一页 SKU 数据，用于选择启用 SKU。
async function fetchSkuPage(
  page: Page,
  authToken: string,
  pageNo: number,
  pageSize: number
): Promise<PagedRecords<SkuRecord>> {
  const response = await page.request.get(`/api/skus?pageNo=${pageNo}&pageSize=${pageSize}`, {
    headers: { Authorization: `Bearer ${authToken}` }
  });
  return parseSuccessEnvelope<PagedRecords<SkuRecord>>(response);
}

// 选择第一个启用 SKU，保证规则可通过后端主数据校验。
async function pickFirstActiveSku(page: Page, authToken: string): Promise<SkuRecord> {
  const pageSize = 50;
  const maxPages = 20;
  for (let pageNo = 1; pageNo <= maxPages; pageNo += 1) {
    const pageData = await fetchSkuPage(page, authToken, pageNo, pageSize);
    const active = (pageData.records || []).find((row) => row.status === 1);
    if (active) {
      return active;
    }
    if (!pageData.records?.length) {
      break;
    }
  }
  throw new Error("未找到可用的启用 SKU，无法创建预警规则");
}

// 按关键词分页查询规则列表，并定位指定 warehouse+sku 的现有规则。
async function findRuleByWarehouseAndSku(
  page: Page,
  authToken: string,
  warehouseId: number,
  skuId: number,
  keyword: string
): Promise<AlertRuleRecord | null> {
  const pageSize = 50;
  const maxPages = 20;
  for (let pageNo = 1; pageNo <= maxPages; pageNo += 1) {
    const response = await page.request.get(
      `/api/inventory/alert-rules?pageNo=${pageNo}&pageSize=${pageSize}&keyword=${encodeURIComponent(keyword)}`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    );
    const pageData = await parseSuccessEnvelope<PagedRecords<AlertRuleRecord>>(response);
    const target = (pageData.records || []).find((row) => row.warehouseId === warehouseId && row.skuId === skuId);
    if (target) {
      return target;
    }
    if (!pageData.records?.length) {
      break;
    }
  }
  return null;
}

// 确保存在可命中预警的规则：阈值固定为 0/0/0 且启用，保证任意库存都会进入预警结果。
export async function ensureInventoryAlertRuleByApi(page: Page): Promise<AlertRuleAnchor> {
  const authToken = await readSessionToken(page);
  const warehouses = await fetchWarehouseOptions(page, authToken);
  expect(warehouses.length).toBeGreaterThan(0);
  const warehouse = warehouses[0];
  const sku = await pickFirstActiveSku(page, authToken);

  const payload = {
    warehouseId: warehouse.id,
    skuId: sku.id,
    minQty: 0,
    safeQty: 0,
    maxQty: 0,
    status: 1,
    remark: `e2e-m5-rule-${Date.now()}`
  };

  const existing = await findRuleByWarehouseAndSku(page, authToken, warehouse.id, sku.id, sku.skuCode);
  const response = existing
    ? await page.request.put(`/api/inventory/alert-rules/${existing.id}`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: payload
      })
    : await page.request.post("/api/inventory/alert-rules", {
        headers: { Authorization: `Bearer ${authToken}` },
        data: payload
      });

  const detail = await parseSuccessEnvelope<AlertRuleRecord>(response);
  expect(detail.id).toBeTruthy();
  expect(detail.status).toBe(1);

  return {
    ruleId: detail.id,
    warehouseId: detail.warehouseId,
    warehouseName: detail.warehouseName,
    skuId: detail.skuId,
    skuCode: detail.skuCode
  };
}
