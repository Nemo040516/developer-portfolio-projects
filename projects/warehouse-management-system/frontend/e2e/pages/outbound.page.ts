/*
 * @file 速览索引
 * @summary 出库页 Page Object，封装出库单查询、提交、确认与状态断言，支撑 M4 E2E 主链路。
 * @core 1. 校验出库页面加载完成
 * @core 2. 按出库单号查询目标单据
 * @core 3. 执行提交与确认出库动作
 * @core 4. 断言状态流转（草稿/已提交/已完成）
 * @entry 先看：assertLoaded、submitByOutboundNo、confirmByOutboundNo、assertStatus
 * @deps 依赖：@playwright/test、前端/src/components/OutboundPanel.vue
 * @state 关键元素：查询框、表格行、二次确认按钮
 * @risk 高风险修改点：按钮文案、状态文案、二次确认弹窗文案
 * @link 相关文件：前端/e2e/tests/m1-m6/m4-outbound-submit-confirm.spec.ts
 */
import { expect, type Locator, type Page } from "@playwright/test";

import { assertToastVisible } from "../utils/feedback";

export class OutboundPage {
  constructor(private readonly page: Page) {}

  // 出库页查询输入框：用于按单号精确过滤目标记录。
  private readonly keywordInput = this.page.getByPlaceholder("按出库单号/目标/仓库搜索");

  // 出库页查询按钮：触发表格刷新。
  private readonly queryButton = this.page.getByRole("button", { name: "查询" });

  // 出库页“新建出库单”按钮：作为页面加载成功的稳定锚点。
  private readonly openCreateButton = this.page.getByRole("button", { name: "新建出库单" });

  // 出库页二次确认按钮：提交/确认共用确认动作。
  private readonly confirmExecuteButton = this.page.getByRole("button", { name: "确认执行" });

  // 出库页表格主体行：用于按单号定位目标单据。
  private readonly tableRows = this.page.locator(".el-table__body-wrapper tbody tr");

  // 通过“行内包含出库单号”定位目标单据行。
  private rowByOutboundNo(outboundNo: string): Locator {
    return this.tableRows.filter({ hasText: outboundNo }).first();
  }

  // 校验页面关键入口可见，确保当前已切换到出库管理页。
  async assertLoaded(): Promise<void> {
    await expect(this.openCreateButton).toBeVisible();
    await expect(this.keywordInput).toBeVisible();
  }

  // 按单号查询并返回目标行，供提交/确认动作复用。
  async searchRowByOutboundNo(outboundNo: string): Promise<Locator> {
    await this.keywordInput.fill(outboundNo);
    await this.queryButton.click();
    const row = this.rowByOutboundNo(outboundNo);
    await expect(row).toBeVisible();
    return row;
  }

  // 执行提交动作：草稿 -> 已提交。
  async submitByOutboundNo(outboundNo: string): Promise<void> {
    const row = await this.searchRowByOutboundNo(outboundNo);
    await row.getByRole("button", { name: "提交" }).click();
    await this.confirmExecuteButton.click();
    await assertToastVisible(this.page, "提交成功");
  }

  // 执行确认出库动作：已提交 -> 已完成。
  async confirmByOutboundNo(outboundNo: string): Promise<void> {
    const row = await this.searchRowByOutboundNo(outboundNo);
    await row.getByRole("button", { name: "确认出库" }).click();
    await this.confirmExecuteButton.click();
    await assertToastVisible(this.page, "确认出库成功");
  }

  // 断言目标单据在列表中的状态文案。
  async assertStatus(outboundNo: string, statusText: "草稿" | "已提交" | "已完成"): Promise<void> {
    const row = await this.searchRowByOutboundNo(outboundNo);
    await expect(row.getByText(statusText)).toBeVisible();
  }
}
