/*
 * @file 速览索引
 * @summary 出库测试数据工具，负责选择“可出库库位库存”并通过后端接口创建草稿出库单。
 * @core 1. 读取当前登录会话 token
 * @core 2. 拉取库位库存与库位主数据并筛选启用库位
 * @core 3. 选择可出库的 warehouse+sku+location 组合
 * @core 4. 创建草稿出库单并返回单号
 * @entry 先看：createOutboundDraftByApi
 * @deps 依赖：@playwright/test、inventory/location/outbound 接口
 * @state 关键数据：warehouseId、skuId、locationId、outboundNo
 * @risk 高风险修改点：分页返回结构、库存字段命名、创建请求字段
 * @link 相关文件：前端/e2e/tests/m1-m6/m4-outbound-submit-confirm.spec.ts
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

type LocationStockRecord = {
  warehouseId: number;
  locationId: number;
  skuId: number;
  onHandQty: number;
};

type LocationRecord = {
  id: number;
  warehouseId: number;
  status: number;
};

type OutboundDraftCandidate = {
  warehouseId: number;
  skuId: number;
  locationId: number;
  qty: number;
};

// 从会话读取 token，保证 API 前置与 UI 场景使用同一账号上下文。
async function readSessionToken(page: Page): Promise<string> {
  const token = await page.evaluate(() => sessionStorage.getItem("wms_token"));
  expect(token).toBeTruthy();
  return String(token);
}

// 统一解析后端 ApiResponse，减少每个请求重复样板代码。
async function parseSuccessEnvelope<T>(response: APIResponse): Promise<T> {
  expect(response.ok()).toBeTruthy();
  const envelope = (await response.json()) as ApiEnvelope<T>;
  expect(envelope.code).toBe(0);
  return envelope.data;
}

// 拉取一页库位库存数据，作为可出库候选来源。
async function fetchLocationStocksPage(
  page: Page,
  authToken: string,
  pageNo: number,
  pageSize: number
): Promise<PagedRecords<LocationStockRecord>> {
  const response = await page.request.get(`/api/inventory/location-stocks?pageNo=${pageNo}&pageSize=${pageSize}`, {
    headers: { Authorization: `Bearer ${authToken}` }
  });
  return parseSuccessEnvelope<PagedRecords<LocationStockRecord>>(response);
}

// 拉取一页库位主数据，用于筛选“启用状态”的库位。
async function fetchLocationsPage(
  page: Page,
  authToken: string,
  pageNo: number,
  pageSize: number
): Promise<PagedRecords<LocationRecord>> {
  const response = await page.request.get(`/api/locations?pageNo=${pageNo}&pageSize=${pageSize}`, {
    headers: { Authorization: `Bearer ${authToken}` }
  });
  return parseSuccessEnvelope<PagedRecords<LocationRecord>>(response);
}

// 拉取全部库位库存分页数据，避免只看第一页导致漏选可用候选。
async function fetchAllLocationStocks(page: Page, authToken: string): Promise<LocationStockRecord[]> {
  const pageSize = 50;
  const maxPages = 20;
  const rows: LocationStockRecord[] = [];

  for (let pageNo = 1; pageNo <= maxPages; pageNo += 1) {
    const pageData = await fetchLocationStocksPage(page, authToken, pageNo, pageSize);
    const records = pageData.records || [];
    rows.push(...records);
    if (!records.length || rows.length >= Number(pageData.total || 0)) {
      break;
    }
  }

  return rows;
}

// 拉取全部库位分页数据，供候选过滤时判断库位是否启用。
async function fetchAllLocations(page: Page, authToken: string): Promise<LocationRecord[]> {
  const pageSize = 50;
  const maxPages = 20;
  const rows: LocationRecord[] = [];

  for (let pageNo = 1; pageNo <= maxPages; pageNo += 1) {
    const pageData = await fetchLocationsPage(page, authToken, pageNo, pageSize);
    const records = pageData.records || [];
    rows.push(...records);
    if (!records.length || rows.length >= Number(pageData.total || 0)) {
      break;
    }
  }

  return rows;
}

// 构建“启用库位 ID 集合”，用于筛除停用库位导致的创建失败。
function buildActiveLocationIdSet(locations: LocationRecord[]): Set<number> {
  const activeIds = new Set<number>();
  for (const row of locations) {
    if (row.status === 1) {
      activeIds.add(row.id);
    }
  }
  return activeIds;
}

// 选择一个“在手库存>0 且库位启用”的组合，供创建出库草稿单。
function pickDraftCandidate(locationStocks: LocationStockRecord[], activeLocationIds: Set<number>): OutboundDraftCandidate {
  const sortedStocks = [...locationStocks].sort((a, b) => Number(b.onHandQty || 0) - Number(a.onHandQty || 0));
  for (const stock of sortedStocks) {
    const availableQty = Number(stock.onHandQty || 0);
    if (availableQty <= 0) {
      continue;
    }
    if (!activeLocationIds.has(stock.locationId)) {
      continue;
    }
    return {
      warehouseId: stock.warehouseId,
      skuId: stock.skuId,
      locationId: stock.locationId,
      qty: Math.min(1, availableQty)
    };
  }

  throw new Error("未找到可用于出库的库位库存（需存在 onHandQty>0 且库位状态为启用）");
}

// 通过 API 创建草稿出库单并返回 outboundNo，供 UI 提交/确认流转使用。
export async function createOutboundDraftByApi(page: Page): Promise<string> {
  const authToken = await readSessionToken(page);
  const [locationStocks, locations] = await Promise.all([
    fetchAllLocationStocks(page, authToken),
    fetchAllLocations(page, authToken)
  ]);

  expect(locationStocks.length).toBeGreaterThan(0);
  const activeLocationIds = buildActiveLocationIdSet(locations);
  expect(activeLocationIds.size).toBeGreaterThan(0);
  const candidate = pickDraftCandidate(locationStocks, activeLocationIds);

  const response = await page.request.post("/api/outbounds", {
    headers: { Authorization: `Bearer ${authToken}` },
    data: {
      warehouseId: candidate.warehouseId,
      outboundType: "SALES",
      targetName: `e2e-m4-target-${Date.now()}`,
      remark: "e2e-m4-draft",
      items: [
        {
          skuId: candidate.skuId,
          locationId: candidate.locationId,
          planQty: candidate.qty,
          actualQty: candidate.qty,
          remark: "e2e-m4-item"
        }
      ]
    }
  });

  const detail = await parseSuccessEnvelope<{ outboundNo: string }>(response);
  expect(detail.outboundNo).toBeTruthy();
  return detail.outboundNo;
}
