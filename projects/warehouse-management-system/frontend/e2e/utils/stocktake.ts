/*
 * @file 速览索引
 * @summary 盘点测试数据工具，负责挑选可用库位库存并通过后端接口创建草稿盘点单。
 * @core 1. 读取当前登录会话 token
 * @core 2. 拉取库位库存、库位主数据、仓库选项与 SKU 主数据
 * @core 3. 选择通过后端校验的 warehouse+sku+location 组合
 * @core 4. 创建草稿盘点单并返回单号
 * @entry 先看：createStocktakeDraftByApi
 * @deps 依赖：@playwright/test、inventory/location/warehouse/sku/stocktake 接口
 * @state 关键数据：warehouseId、skuId、locationId、countQty、stocktakeNo
 * @risk 高风险修改点：分页结构、主数据状态字段、盘点创建请求字段
 * @link 相关文件：前端/e2e/tests/m1-m6/m5-stocktake-submit-confirm.spec.ts
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

type WarehouseOption = {
  id: number;
  warehouseCode: string;
  warehouseName: string;
};

type SkuRecord = {
  id: number;
  status: number;
};

type StocktakeDraftCandidate = {
  warehouseId: number;
  locationId: number;
  skuId: number;
  countQty: number;
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

// 拉取一页库位库存数据，作为盘点明细候选来源。
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

// 拉取全部库位库存分页数据，避免只看第一页导致候选不足。
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

// 拉取一页库位主数据，用于筛选启用库位。
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

// 拉取全部库位主数据，构建启用库位集合。
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

// 拉取可用仓库选项，作为盘点单 warehouse 合法性校验依据。
async function fetchWarehouseOptions(page: Page, authToken: string): Promise<WarehouseOption[]> {
  const response = await page.request.get("/api/warehouses/options", {
    headers: { Authorization: `Bearer ${authToken}` }
  });
  return parseSuccessEnvelope<WarehouseOption[]>(response);
}

// 拉取一页 SKU 数据，用于筛选启用 SKU。
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

// 构建启用 SKU 集合，避免选择停用 SKU 导致创建失败。
async function buildActiveSkuIdSet(page: Page, authToken: string): Promise<Set<number>> {
  const pageSize = 50;
  const maxPages = 20;
  const activeSkuIds = new Set<number>();

  for (let pageNo = 1; pageNo <= maxPages; pageNo += 1) {
    const pageData = await fetchSkuPage(page, authToken, pageNo, pageSize);
    for (const row of pageData.records || []) {
      if (row.status === 1) {
        activeSkuIds.add(row.id);
      }
    }
    if (!pageData.records?.length) {
      break;
    }
  }

  return activeSkuIds;
}

// 选择通过后端校验的盘点组合，优先使用在手库存较高的数据行提升稳定性。
function pickDraftCandidate(
  locationStocks: LocationStockRecord[],
  activeWarehouseIds: Set<number>,
  activeLocationIds: Set<number>,
  activeSkuIds: Set<number>
): StocktakeDraftCandidate {
  const sortedStocks = [...locationStocks].sort((a, b) => Number(b.onHandQty || 0) - Number(a.onHandQty || 0));
  for (const stock of sortedStocks) {
    if (!activeWarehouseIds.has(stock.warehouseId)) {
      continue;
    }
    if (!activeLocationIds.has(stock.locationId)) {
      continue;
    }
    if (!activeSkuIds.has(stock.skuId)) {
      continue;
    }
    return {
      warehouseId: stock.warehouseId,
      locationId: stock.locationId,
      skuId: stock.skuId,
      // 采用“实盘=账面”创建盘点明细，减少对环境库存的扰动。
      countQty: Math.max(0, Number(stock.onHandQty || 0))
    };
  }

  throw new Error("未找到可用于盘点建单的合法库存组合（需仓库/库位/SKU均为启用状态）");
}

// 通过 API 创建草稿盘点单并返回 stocktakeNo，供 UI 提交/确认流转使用。
export async function createStocktakeDraftByApi(page: Page): Promise<string> {
  const authToken = await readSessionToken(page);
  const [locationStocks, locations, warehouses, activeSkuIds] = await Promise.all([
    fetchAllLocationStocks(page, authToken),
    fetchAllLocations(page, authToken),
    fetchWarehouseOptions(page, authToken),
    buildActiveSkuIdSet(page, authToken)
  ]);

  expect(locationStocks.length).toBeGreaterThan(0);
  expect(warehouses.length).toBeGreaterThan(0);
  expect(activeSkuIds.size).toBeGreaterThan(0);

  const activeWarehouseIds = new Set<number>(warehouses.map((row) => row.id));
  const activeLocationIds = new Set<number>(locations.filter((row) => row.status === 1).map((row) => row.id));
  const candidate = pickDraftCandidate(locationStocks, activeWarehouseIds, activeLocationIds, activeSkuIds);

  const response = await page.request.post("/api/stocktakes", {
    headers: { Authorization: `Bearer ${authToken}` },
    data: {
      warehouseId: candidate.warehouseId,
      scopeType: "BY_WAREHOUSE",
      remark: "e2e-m5-draft",
      items: [
        {
          skuId: candidate.skuId,
          locationId: candidate.locationId,
          countQty: candidate.countQty,
          reason: "e2e-m5",
          remark: "e2e-m5-item"
        }
      ]
    }
  });

  const detail = await parseSuccessEnvelope<{ stocktakeNo: string }>(response);
  expect(detail.stocktakeNo).toBeTruthy();
  return detail.stocktakeNo;
}
