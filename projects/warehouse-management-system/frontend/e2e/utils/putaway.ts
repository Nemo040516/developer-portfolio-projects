/*
 * @file 速览索引
 * @summary 上架测试数据工具，负责选择“可上架库存”并通过后端接口创建草稿上架单。
 * @core 1. 读取当前登录会话 token
 * @core 2. 计算 warehouse+sku 的可上架数量（总库存 - 已分配库位库存）
 * @core 3. 匹配可用库位并创建草稿上架单
 * @entry 先看：createPutawayDraftByApi
 * @deps 依赖：@playwright/test、inventory/location/putaway 接口
 * @state 关键数据：warehouseId、skuId、locationId、putawayNo
 * @risk 高风险修改点：库存接口返回字段、可上架计算口径、创建请求字段
 * @link 相关文件：前端/e2e/tests/m1-m6/m3-putaway-submit-confirm.spec.ts
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

type InventoryStockRecord = {
  warehouseId: number;
  skuId: number;
  onHandQty: number;
};

type LocationStockRecord = {
  warehouseId: number;
  skuId: number;
  onHandQty: number;
};

type LocationRecord = {
  id: number;
  warehouseId: number;
  status: number;
};

type PutawayDraftCandidate = {
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

// 拉取库存台账（仓库+SKU）分页数据，作为“总库存”来源。
async function fetchInventoryStocks(page: Page, authToken: string): Promise<InventoryStockRecord[]> {
  const response = await page.request.get("/api/inventory/stocks?pageNo=1&pageSize=500", {
    headers: { Authorization: `Bearer ${authToken}` }
  });
  const data = await parseSuccessEnvelope<PagedRecords<InventoryStockRecord>>(response);
  return data.records || [];
}

// 拉取库位库存（仓库+SKU+库位）分页数据，作为“已分配库存”来源。
async function fetchLocationStocks(page: Page, authToken: string): Promise<LocationStockRecord[]> {
  const response = await page.request.get("/api/inventory/location-stocks?pageNo=1&pageSize=1000", {
    headers: { Authorization: `Bearer ${authToken}` }
  });
  const data = await parseSuccessEnvelope<PagedRecords<LocationStockRecord>>(response);
  return data.records || [];
}

// 拉取库位主数据，用于匹配可写入的目标库位。
async function fetchLocations(page: Page, authToken: string): Promise<LocationRecord[]> {
  const response = await page.request.get("/api/locations?pageNo=1&pageSize=500", {
    headers: { Authorization: `Bearer ${authToken}` }
  });
  const data = await parseSuccessEnvelope<PagedRecords<LocationRecord>>(response);
  return data.records || [];
}

// 计算每个 warehouse+sku 当前已分配在库位上的库存总量。
function buildAllocatedQtyMap(locationStocks: LocationStockRecord[]): Map<string, number> {
  const allocatedMap = new Map<string, number>();
  for (const row of locationStocks) {
    const key = `${row.warehouseId}-${row.skuId}`;
    allocatedMap.set(key, (allocatedMap.get(key) || 0) + Number(row.onHandQty || 0));
  }
  return allocatedMap;
}

// 选择一个“可上架库存>0 且存在可用库位”的组合，供创建上架草稿单。
function pickDraftCandidate(
  inventoryStocks: InventoryStockRecord[],
  allocatedMap: Map<string, number>,
  locations: LocationRecord[]
): PutawayDraftCandidate {
  for (const stock of inventoryStocks) {
    const key = `${stock.warehouseId}-${stock.skuId}`;
    const totalQty = Number(stock.onHandQty || 0);
    const allocatedQty = allocatedMap.get(key) || 0;
    const availableQty = totalQty - allocatedQty;

    if (availableQty <= 0) {
      continue;
    }

    const location = locations.find((row) => row.status === 1 && row.warehouseId === stock.warehouseId);
    if (!location) {
      continue;
    }

    return {
      warehouseId: stock.warehouseId,
      skuId: stock.skuId,
      locationId: location.id,
      qty: Math.min(1, availableQty)
    };
  }

  throw new Error("未找到可用于上架的库存组合（总库存需大于已分配库存，且仓库下存在启用库位）");
}

// 通过 API 创建草稿上架单并返回 putawayNo，供 UI 提交/确认流转使用。
export async function createPutawayDraftByApi(page: Page): Promise<string> {
  const authToken = await readSessionToken(page);
  const [inventoryStocks, locationStocks, locations] = await Promise.all([
    fetchInventoryStocks(page, authToken),
    fetchLocationStocks(page, authToken),
    fetchLocations(page, authToken)
  ]);

  expect(inventoryStocks.length).toBeGreaterThan(0);
  const allocatedMap = buildAllocatedQtyMap(locationStocks);
  const candidate = pickDraftCandidate(inventoryStocks, allocatedMap, locations);

  const response = await page.request.post("/api/putaways", {
    headers: { Authorization: `Bearer ${authToken}` },
    data: {
      warehouseId: candidate.warehouseId,
      sourceType: "INBOUND",
      sourceOrderId: null,
      sourceOrderNo: `e2e-m3-${Date.now()}`,
      remark: "e2e-m3-draft",
      items: [
        {
          skuId: candidate.skuId,
          locationId: candidate.locationId,
          planQty: candidate.qty,
          actualQty: 0,
          remark: "e2e-m3-item"
        }
      ]
    }
  });

  const detail = await parseSuccessEnvelope<{ putawayNo: string }>(response);
  expect(detail.putawayNo).toBeTruthy();
  return detail.putawayNo;
}
