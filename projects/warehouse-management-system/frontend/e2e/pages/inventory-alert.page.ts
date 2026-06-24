/*
 * @file 速览索引
 * @summary 库存预警页 Page Object，封装“预警规则/预警列表”查询与结果断言，支撑 M5 E2E 主链路。
 * @core 1. 校验库存预警页加载完成
 * @core 2. 切换“预警列表/预警规则”页签
 * @core 3. 按关键词查询规则与预警结果
 * @core 4. 断言指定仓库+SKU在规则/预警列表中可见
 * @entry 先看：assertLoaded、openRulesTab、searchRulesByKeyword、searchAlertsByKeyword
 * @deps 依赖：@playwright/test、前端/src/components/InventoryAlertPanel.vue
 * @state 关键元素：页签、查询输入框、表格行
 * @risk 高风险修改点：页签文案、查询占位符、表格 DOM 结构
 * @link 相关文件：前端/e2e/tests/m1-m6/m5-inventory-alert-rule-upsert.spec.ts
 */
import { expect, type Locator, type Page } from "@playwright/test";

export class InventoryAlertPage {
  constructor(private readonly page: Page) {}

  // “预警列表”页签：默认展示库存预警结果。
  private readonly alertsTab = this.page.getByRole("tab", { name: "预警列表" });

  // “预警规则”页签：管理员/仓库员可维护阈值配置。
  private readonly rulesTab = this.page.getByRole("tab", { name: "预警规则" });

  // 预警列表关键词输入框：按仓库/SKU过滤预警结果。
  private readonly alertsKeywordInput = this.page.getByPlaceholder("按仓库/SKU搜索", { exact: true });

  // 预警规则关键词输入框：按仓库/SKU过滤规则列表。
  private readonly rulesKeywordInput = this.page.getByPlaceholder("按仓库/SKU搜索规则", { exact: true });

  // 表格主体行：规则与预警列表都复用 Element Plus 表格结构。
  private readonly tableRows = this.page.locator(".el-table__body-wrapper:visible tbody tr");

  // 页面加载断言：确认关键说明与默认页签可见。
  async assertLoaded(): Promise<void> {
    await expect(
      this.page.getByText("M5 说明：本页用于维护安全库存阈值并查看缺货/低库存/超储预警。")
    ).toBeVisible();
    await expect(this.alertsTab).toBeVisible();
    await expect(this.alertsKeywordInput).toBeVisible();
  }

  // 切换到“预警规则”页签，并等待规则查询输入可见。
  async openRulesTab(): Promise<void> {
    await this.rulesTab.click();
    await expect(this.rulesKeywordInput).toBeVisible();
  }

  // 切换到“预警列表”页签，并等待预警查询输入可见。
  async openAlertsTab(): Promise<void> {
    await this.alertsTab.click();
    await expect(this.alertsKeywordInput).toBeVisible();
  }

  // 在规则页签按关键词查询，便于精准定位目标仓库+SKU规则。
  async searchRulesByKeyword(keyword: string): Promise<void> {
    await this.openRulesTab();
    await this.rulesKeywordInput.fill(keyword);
    await this.page.getByRole("button", { name: "查询" }).click();
  }

  // 在预警页签按关键词查询，便于定位目标仓库+SKU预警记录。
  async searchAlertsByKeyword(keyword: string): Promise<void> {
    await this.openAlertsTab();
    await this.alertsKeywordInput.fill(keyword);
    await this.page.getByRole("button", { name: "查询" }).click();
  }

  // 断言规则列表存在目标仓库+SKU的规则行。
  async assertRuleVisible(warehouseName: string, skuCode: string): Promise<void> {
    const row = this.tableRows.filter({ hasText: warehouseName }).filter({ hasText: skuCode }).first();
    await expect(row).toBeVisible();
  }

  // 断言规则行处于启用状态，保证其会参与预警计算。
  async assertRuleEnabled(warehouseName: string, skuCode: string): Promise<void> {
    const row = this.tableRows.filter({ hasText: warehouseName }).filter({ hasText: skuCode }).first();
    await expect(row.getByText("启用")).toBeVisible();
  }

  // 断言预警列表存在目标仓库+SKU记录，证明规则已生效并命中预警口径。
  async assertAlertVisible(warehouseName: string, skuCode: string): Promise<void> {
    const row = this.tableRows.filter({ hasText: warehouseName }).filter({ hasText: skuCode }).first();
    await expect(row).toBeVisible();
  }
}
