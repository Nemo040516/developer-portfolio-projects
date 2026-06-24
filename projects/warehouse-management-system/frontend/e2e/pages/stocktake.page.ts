/*
 * @file 速览索引
 * @summary 盘点页 Page Object，封装盘点单查询、提交、确认与状态断言，支撑 M5 盘点链路。
 * @core 1. 校验盘点页面加载完成
 * @core 2. 按盘点单号查询目标单据
 * @core 3. 执行提交与确认盘点动作
 * @core 4. 断言状态流转（草稿/已提交/已完成）
 * @entry 先看：assertLoaded、submitByStocktakeNo、confirmByStocktakeNo、assertStatus
 * @deps 依赖：@playwright/test、前端/src/components/StocktakePanel.vue
 * @state 关键元素：查询框、表格行、二次确认按钮
 * @risk 高风险修改点：按钮文案、状态文案、二次确认弹窗文案
 * @link 相关文件：前端/e2e/tests/m1-m6/m5-stocktake-submit-confirm.spec.ts
 */
import { expect, type Locator, type Page } from "@playwright/test";

import { assertToastVisible } from "../utils/feedback";

export class StocktakePage {
  constructor(private readonly page: Page) {}

  // 盘点页查询输入框：用于按单号过滤目标记录。
  private readonly keywordInput = this.page.getByPlaceholder("按盘点单号/仓库搜索");

  // 盘点页查询按钮：触发列表刷新。
  private readonly queryButton = this.page.getByRole("button", { name: "查询" });

  // 盘点页“新建盘点单”按钮：作为页面加载锚点。
  private readonly openCreateButton = this.page.getByRole("button", { name: "新建盘点单" });

  // 二次确认弹窗“确认执行”按钮：提交/确认共用。
  private readonly confirmExecuteButton = this.page.getByRole("button", { name: "确认执行" });

  // 盘点页表格主体行：用于按单号定位目标单据。
  private readonly tableRows = this.page.locator(".el-table__body-wrapper tbody tr");

  // 通过“行内包含盘点单号”定位目标行。
  private rowByStocktakeNo(stocktakeNo: string): Locator {
    return this.tableRows.filter({ hasText: stocktakeNo }).first();
  }

  // 校验页面关键入口可见，确保已进入库存盘点页。
  async assertLoaded(): Promise<void> {
    await expect(this.openCreateButton).toBeVisible();
    await expect(this.keywordInput).toBeVisible();
  }

  // 按单号查询并返回目标行，供状态流转动作复用。
  async searchRowByStocktakeNo(stocktakeNo: string): Promise<Locator> {
    await this.keywordInput.fill(stocktakeNo);
    await this.queryButton.click();
    const row = this.rowByStocktakeNo(stocktakeNo);
    await expect(row).toBeVisible();
    return row;
  }

  // 执行提交动作：草稿 -> 已提交。
  async submitByStocktakeNo(stocktakeNo: string): Promise<void> {
    const row = await this.searchRowByStocktakeNo(stocktakeNo);
    await row.getByRole("button", { name: "提交" }).click();
    await this.confirmExecuteButton.click();
    await assertToastVisible(this.page, "提交成功");
  }

  // 执行确认盘点动作：已提交 -> 已完成。
  async confirmByStocktakeNo(stocktakeNo: string): Promise<void> {
    const row = await this.searchRowByStocktakeNo(stocktakeNo);
    await row.getByRole("button", { name: "确认盘点" }).click();
    await this.confirmExecuteButton.click();
    await assertToastVisible(this.page, "确认盘点成功");
  }

  // 断言目标单据在列表中的状态文案。
  async assertStatus(stocktakeNo: string, statusText: "草稿" | "已提交" | "已完成"): Promise<void> {
    const row = await this.searchRowByStocktakeNo(stocktakeNo);
    await expect(row.getByText(statusText)).toBeVisible();
  }
}
