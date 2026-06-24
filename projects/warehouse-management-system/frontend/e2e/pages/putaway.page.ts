/*
 * @file 速览索引
 * @summary 上架页 Page Object，封装上架单查询、提交、确认与状态断言，支撑 M3 E2E 主链路。
 * @core 1. 校验上架页面加载完成
 * @core 2. 按上架单号查询目标单据
 * @core 3. 执行提交与确认上架动作
 * @core 4. 断言状态流转（草稿/已提交/已完成）
 * @entry 先看：assertLoaded、submitByPutawayNo、confirmByPutawayNo、assertStatus
 * @deps 依赖：@playwright/test、前端/src/components/PutawayPanel.vue
 * @state 关键元素：查询框、表格行、二次确认按钮
 * @risk 高风险修改点：按钮文案、状态文案、二次确认弹窗文案
 * @link 相关文件：前端/e2e/tests/m1-m6/m3-putaway-submit-confirm.spec.ts
 */
import { expect, type Locator, type Page } from "@playwright/test";

import { assertToastVisible } from "../utils/feedback";

export class PutawayPage {
  constructor(private readonly page: Page) {}

  // 上架页查询输入框：用于按单号精确过滤目标记录。
  private readonly keywordInput = this.page.getByPlaceholder("按上架单号/来源单号/仓库搜索");

  // 上架页查询按钮：触发列表刷新。
  private readonly queryButton = this.page.getByRole("button", { name: "查询" });

  // 上架页“新建上架单”按钮：用于页面加载完成的入口断言。
  private readonly openCreateButton = this.page.getByRole("button", { name: "新建上架单" });

  // 上架页二次确认按钮：提交/确认共用确认动作。
  private readonly confirmExecuteButton = this.page.getByRole("button", { name: "确认执行" });

  // 上架页表格行定位器：用于按单号筛选目标行。
  private readonly tableRows = this.page.locator(".el-table__body-wrapper tbody tr");

  // 通过“行内包含上架单号”定位目标单据行。
  private rowByPutawayNo(putawayNo: string): Locator {
    return this.tableRows.filter({ hasText: putawayNo }).first();
  }

  // 校验页面关键入口可见，确保当前已切换到上架管理页。
  async assertLoaded(): Promise<void> {
    await expect(this.openCreateButton).toBeVisible();
    await expect(this.keywordInput).toBeVisible();
  }

  // 按单号查询并返回目标行，供后续提交/确认动作复用。
  async searchRowByPutawayNo(putawayNo: string): Promise<Locator> {
    await this.keywordInput.fill(putawayNo);
    await this.queryButton.click();
    const row = this.rowByPutawayNo(putawayNo);
    await expect(row).toBeVisible();
    return row;
  }

  // 执行提交动作：草稿 -> 已提交。
  async submitByPutawayNo(putawayNo: string): Promise<void> {
    const row = await this.searchRowByPutawayNo(putawayNo);
    await row.getByRole("button", { name: "提交" }).click();
    await this.confirmExecuteButton.click();
    await assertToastVisible(this.page, "提交成功");
  }

  // 执行确认上架动作：已提交 -> 已完成。
  async confirmByPutawayNo(putawayNo: string): Promise<void> {
    const row = await this.searchRowByPutawayNo(putawayNo);
    await row.getByRole("button", { name: "确认上架" }).click();
    await this.confirmExecuteButton.click();
    await assertToastVisible(this.page, "确认上架成功");
  }

  // 断言目标单据在列表中的状态文案。
  async assertStatus(putawayNo: string, statusText: "草稿" | "已提交" | "已完成"): Promise<void> {
    const row = await this.searchRowByPutawayNo(putawayNo);
    await expect(row.getByText(statusText)).toBeVisible();
  }
}
