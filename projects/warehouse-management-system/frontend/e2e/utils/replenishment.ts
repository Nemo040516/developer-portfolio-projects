/*
 * @file 速览索引
 * @summary 补货测试数据工具，负责选择可用仓库并通过后端接口生成待确认补货计划。
 * @core 1. 读取当前登录会话 token
 * @core 2. 拉取可用仓库并选择生成目标
 * @core 3. 调用补货生成接口创建待确认计划
 * @core 4. 返回计划号与参数锚点供 UI 重算/流转复用
 * @entry 先看：createReplenishmentDraftByApi
 * @deps 依赖：@playwright/test、warehouse/replenishment 接口
 * @state 关键数据：planNo、calcDays、leadTimeDays、safetyDays
 * @risk 高风险修改点：接口响应结构、仓库选项字段、生成请求参数
 * @link 相关文件：前端/e2e/tests/m1-m6/m6-replenishment-recalculate-confirm-draft.spec.ts
 */
import { expect, type APIResponse, type Page } from "@playwright/test";

type ApiEnvelope<T> = {
  code: number;
  message: string;
  data: T;
};

type WarehouseOption = {
  id: number;
  warehouseCode: string;
  warehouseName: string;
};

type ReplenishmentDetail = {
  id: number;
  planNo: string;
  warehouseId: number;
  warehouseName: string;
  status: number;
  calcDays: number;
  leadTimeDays: number;
  safetyDays: number;
};

export type ReplenishmentDraftAnchor = {
  id: number;
  planNo: string;
  warehouseId: number;
  warehouseName: string;
  calcDays: number;
  leadTimeDays: number;
  safetyDays: number;
};

// 从会话读取 token，确保 API 前置与 UI 场景使用同一账号上下文。
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

// 拉取可用仓库选项，作为补货计划生成目标。
async function fetchWarehouseOptions(page: Page, authToken: string): Promise<WarehouseOption[]> {
  const response = await page.request.get("/api/warehouses/options", {
    headers: { Authorization: `Bearer ${authToken}` }
  });
  return parseSuccessEnvelope<WarehouseOption[]>(response);
}

// 通过 API 生成待确认补货计划并回传锚点信息，供 UI 重算/确认/转采购链路复用。
export async function createReplenishmentDraftByApi(page: Page): Promise<ReplenishmentDraftAnchor> {
  const authToken = await readSessionToken(page);
  const warehouses = await fetchWarehouseOptions(page, authToken);
  expect(warehouses.length).toBeGreaterThan(0);
  const warehouse = warehouses[0];

  const response = await page.request.post("/api/replenishments/calculate", {
    headers: { Authorization: `Bearer ${authToken}` },
    data: {
      warehouseId: warehouse.id,
      calcDays: 15,
      leadTimeDays: 3,
      safetyDays: 2,
      remark: `e2e-m6-${Date.now()}`
    }
  });

  const detail = await parseSuccessEnvelope<ReplenishmentDetail>(response);
  expect(detail.planNo).toBeTruthy();
  expect(detail.status).toBe(0);

  return {
    id: detail.id,
    planNo: detail.planNo,
    warehouseId: detail.warehouseId,
    warehouseName: detail.warehouseName,
    calcDays: detail.calcDays,
    leadTimeDays: detail.leadTimeDays,
    safetyDays: detail.safetyDays
  };
}
