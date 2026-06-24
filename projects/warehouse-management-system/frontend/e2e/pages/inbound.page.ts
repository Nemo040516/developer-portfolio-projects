/*
 * @file 速览索引
 * @summary 入库页 Page Object，封装入库单创建、查询、提交、确认等核心交互，支撑 M2 E2E 主链路。
 * @core 1. 新建草稿入库单并回传单号
 * @core 2. 按单号查询并定位目标行
 * @core 3. 执行提交与确认入库动作
 * @core 4. 断言单据状态流转（草稿/已提交/已入库）
 * @entry 先看：createDraftInboundOrder、submitByInboundNo、confirmByInboundNo、assertStatus
 * @deps 依赖：@playwright/test、前端/src/components/InboundPanel.vue
 * @state 关键元素：查询框、表格行、二次确认弹窗、创建对话框
 * @risk 高风险修改点：按钮文案、弹窗确认文案、el-select 下拉结构、列表列顺序
 * @link 相关文件：前端/e2e/tests/m1-m6/m2-inbound-submit-confirm.spec.ts
 */
import { expect, type Locator, type Page } from "@playwright/test";

import { assertToastVisible } from "../utils/feedback";

export class InboundPage {
  constructor(private readonly page: Page) {}

  // 入库页查询输入框：用于按单号精确筛选目标单据。
  private readonly keywordInput = this.page.getByPlaceholder("按入库单号/供应商搜索");

  // 入库页“查询”按钮：触发列表刷新。
  private readonly queryButton = this.page.getByRole("button", { name: "查询" });

  // 入库页“新建入库单”按钮：打开创建对话框。
  private readonly openCreateButton = this.page.getByRole("button", { name: "新建入库单" });

  // 入库页创建对话框标题：用于确认弹窗已打开。
  private readonly createDialogTitle = this.page.getByRole("heading", { name: "新建入库单" });

  // 入库页对话框保存按钮：提交创建请求。
  private readonly dialogSaveButton = this.page.getByRole("button", { name: "保存" });

  // 二次确认弹窗确认按钮：用于提交/确认动作。
  private readonly confirmExecuteButton = this.page.getByRole("button", { name: "确认执行" });

  // 入库表格主体行：所有订单行的基础定位器。
  private readonly tableRows = this.page.locator(".el-table__body-wrapper tbody tr");

  // 通过“行内包含单号”定位目标订单行，避免依赖固定行号。
  private rowByInboundNo(inboundNo: string): Locator {
    return this.tableRows.filter({ hasText: inboundNo }).first();
  }

  // 打开指定字段对应的下拉框并选择第一项可用选项，适配供应商/仓库字段。
  private async selectFirstOptionByFormLabel(labelText: string): Promise<void> {
    const formItem = this.page.locator(".el-form-item", { hasText: labelText }).first();
    await expect(formItem).toBeVisible();
    await formItem.locator(".el-select").first().click();
    const visibleDropdown = this.page.locator(".el-select-dropdown:visible").last();
    const firstEnabledOption = visibleDropdown.locator(".el-select-dropdown__item:not(.is-disabled)").first();
    await expect(firstEnabledOption).toBeVisible();
    // Element Plus 下拉项存在展开/收起动画，使用原生 click 可规避“元素不稳定”重试超时。
    await firstEnabledOption.evaluate((node) => (node as HTMLElement).click());
  }

  // 选择明细首行 SKU：创建最小可用入库单所需关键动作。
  private async selectFirstSkuForFirstItemRow(): Promise<void> {
    const firstItemRow = this.page.locator(".el-dialog .el-table__body-wrapper tbody tr").first();
    await expect(firstItemRow).toBeVisible();
    await firstItemRow.locator(".el-select").first().click();
    const visibleDropdown = this.page.locator(".el-select-dropdown:visible").last();
    const firstEnabledOption = visibleDropdown.locator(".el-select-dropdown__item:not(.is-disabled)").first();
    await expect(firstEnabledOption).toBeVisible();
    // 明细行 SKU 选择同样采用原生 click，降低动效引发的偶发失败。
    await firstEnabledOption.evaluate((node) => (node as HTMLElement).click());
  }

  // 查询并定位目标订单行：后续状态流转动作都以此为前置步骤。
  async searchRowByInboundNo(inboundNo: string): Promise<Locator> {
    await this.keywordInput.fill(inboundNo);
    await this.queryButton.click();
    const row = this.rowByInboundNo(inboundNo);
    await expect(row).toBeVisible();
    return row;
  }

  // 断言入库页已加载，确保测试在正确页面执行。
  async assertLoaded(): Promise<void> {
    await expect(this.openCreateButton).toBeVisible();
    await expect(this.keywordInput).toBeVisible();
  }

  // 创建一张最小可用草稿入库单，并返回后端生成的入库单号供后续流转。
  async createDraftInboundOrder(): Promise<string> {
    await this.openCreateButton.click();
    await expect(this.createDialogTitle).toBeVisible();

    // 供应商/仓库/SKU 都选择第一个可用项，确保创建流程最短可回归。
    await this.selectFirstOptionByFormLabel("供应商");
    await this.selectFirstOptionByFormLabel("仓库");
    await this.selectFirstSkuForFirstItemRow();

    // 通过监听 POST 响应提取 inboundNo，避免依赖列表排序推断单号。
    const createResponsePromise = this.page.waitForResponse(
      (response) => response.request().method() === "POST" && response.url().includes("/api/inbounds")
    );
    await this.dialogSaveButton.click();
    const createResponse = await createResponsePromise;
    const createResponseBody = await createResponse.json();
    const inboundNo = createResponseBody?.data?.inboundNo;

    await assertToastVisible(this.page, "入库单创建成功");
    await expect(this.createDialogTitle).toBeHidden();

    expect(typeof inboundNo).toBe("string");
    expect(String(inboundNo).length).toBeGreaterThan(0);
    return String(inboundNo);
  }

  // 执行提交动作：草稿 -> 已提交。
  async submitByInboundNo(inboundNo: string): Promise<void> {
    const row = await this.searchRowByInboundNo(inboundNo);
    await row.getByRole("button", { name: "提交" }).click();
    await this.confirmExecuteButton.click();
    await assertToastVisible(this.page, "提交成功");
  }

  // 执行确认入库动作：已提交 -> 已入库。
  async confirmByInboundNo(inboundNo: string): Promise<void> {
    const row = await this.searchRowByInboundNo(inboundNo);
    await row.getByRole("button", { name: "确认入库" }).click();
    await this.confirmExecuteButton.click();
    await assertToastVisible(this.page, "入库确认成功");
  }

  // 校验目标单据在列表中的状态文案是否符合预期。
  async assertStatus(inboundNo: string, statusText: "草稿" | "已提交" | "已入库"): Promise<void> {
    const row = await this.searchRowByInboundNo(inboundNo);
    await expect(row.getByText(statusText)).toBeVisible();
  }
}
