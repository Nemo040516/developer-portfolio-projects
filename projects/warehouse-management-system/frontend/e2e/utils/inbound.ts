/*
 * @file 速览索引
 * @summary 入库测试数据工具，负责通过后端接口创建最小可用草稿入库单，供 M2 UI 流转用例复用。
 * @core 1. 读取当前会话 token
 * @core 2. 拉取可用供应商/仓库/SKU 基础 ID
 * @core 3. 创建草稿入库单并返回单号
 * @entry 先看：createInboundDraftByApi
 * @deps 依赖：@playwright/test、前端后端登录态（sessionStorage.wms_token）
 * @state 关键数据：supplierId、warehouseId、skuId、inboundNo
 * @risk 高风险修改点：接口返回结构（code/data）与字段命名变更
 * @link 相关文件：前端/e2e/tests/m1-m6/m2-inbound-submit-confirm.spec.ts
 */
import { expect, type Page, type APIResponse } from "@playwright/test";

type ApiEnvelope<T> = {
  code: number;
  message: string;
  data: T;
};

// 从浏览器会话中提取当前登录 token，确保 API 前置与 UI 使用同一账号上下文。
async function readSessionToken(page: Page): Promise<string> {
  const token = await page.evaluate(() => sessionStorage.getItem("wms_token"));
  expect(token).toBeTruthy();
  return String(token);
}

// 统一解析后端 ApiResponse，确保失败时能在测试里快速暴露结构问题。
async function parseSuccessEnvelope<T>(response: APIResponse): Promise<T> {
  expect(response.ok()).toBeTruthy();
  const envelope = (await response.json()) as ApiEnvelope<T>;
  expect(envelope.code).toBe(0);
  return envelope.data;
}

// 拉取一个可用供应商 ID，作为入库主单创建入参。
async function fetchFirstSupplierId(page: Page, authToken: string): Promise<number> {
  const response = await page.request.get("/api/suppliers?pageNo=1&pageSize=1", {
    headers: { Authorization: `Bearer ${authToken}` }
  });
  const pageData = await parseSuccessEnvelope<{ records: Array<{ id: number }> }>(response);
  expect(pageData.records.length).toBeGreaterThan(0);
  return pageData.records[0].id;
}

// 拉取一个可用仓库 ID，作为入库主单创建入参。
async function fetchFirstWarehouseId(page: Page, authToken: string): Promise<number> {
  const response = await page.request.get("/api/warehouses/options", {
    headers: { Authorization: `Bearer ${authToken}` }
  });
  const options = await parseSuccessEnvelope<Array<{ id: number }>>(response);
  expect(options.length).toBeGreaterThan(0);
  return options[0].id;
}

// 拉取一个可用 SKU ID，作为入库明细创建入参。
async function fetchFirstSkuId(page: Page, authToken: string): Promise<number> {
  const response = await page.request.get("/api/skus?pageNo=1&pageSize=1", {
    headers: { Authorization: `Bearer ${authToken}` }
  });
  const pageData = await parseSuccessEnvelope<{ records: Array<{ id: number }> }>(response);
  expect(pageData.records.length).toBeGreaterThan(0);
  return pageData.records[0].id;
}

// 通过 API 创建草稿入库单，返回后端生成的 inboundNo，供 UI 提交/确认流转。
export async function createInboundDraftByApi(page: Page): Promise<string> {
  const authToken = await readSessionToken(page);
  const [supplierId, warehouseId, skuId] = await Promise.all([
    fetchFirstSupplierId(page, authToken),
    fetchFirstWarehouseId(page, authToken),
    fetchFirstSkuId(page, authToken)
  ]);

  const response = await page.request.post("/api/inbounds", {
    headers: { Authorization: `Bearer ${authToken}` },
    data: {
      supplierId,
      warehouseId,
      remark: `e2e-m2-${Date.now()}`,
      items: [
        {
          skuId,
          planQty: 1,
          receivedQty: 1,
          remark: "e2e-m2-item"
        }
      ]
    }
  });
  const detail = await parseSuccessEnvelope<{ inboundNo: string }>(response);
  expect(detail.inboundNo).toBeTruthy();
  return detail.inboundNo;
}
