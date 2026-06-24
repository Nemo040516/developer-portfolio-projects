/*
 * @file 速览索引
 * @summary 补货页 Page Object，封装补货计划查询、重算、确认、转采购草稿与状态断言，支撑 M6 E2E 主链路。
 * @core 1. 校验智能补货页面加载完成
 * @core 2. 按计划号查询目标补货计划
 * @core 3. 执行重算、确认与转采购草稿动作
 * @core 4. 断言状态流转与采购草稿号生成
 * @entry 先看：assertLoaded、recalculateByPlanNo、confirmByPlanNo、toPurchaseDraftByPlanNo、assertStatus
 * @deps 依赖：@playwright/test、前端/src/components/ReplenishmentPanel.vue
 * @state 关键元素：查询框、表格行、重算弹窗、二次确认按钮
 * @risk 高风险修改点：按钮文案、状态文案、重算弹窗结构、详情弹窗交互
 * @link 相关文件：前端/e2e/tests/m1-m6/m6-replenishment-recalculate-confirm-draft.spec.ts
 */
import { expect, type Locator, type Page } from "@playwright/test";

import { assertToastVisible } from "../utils/feedback";

type RecalculatePayload = {
  calcDays: number;
  leadTimeDays: number;
  safetyDays: number;
  remark: string;
};

export class ReplenishmentPage {
  constructor(private readonly page: Page) {}

  // 智能补货查询输入框：用于按计划号/仓库定位目标记录。
  private readonly keywordInput = this.page.getByPlaceholder("按计划号/仓库搜索", { exact: true });

  // 补货页查询按钮：触发列表刷新。
  private readonly queryButton = this.page.getByRole("button", { name: "查询" });

  // 补货页“生成建议”按钮：作为页面加载锚点。
  private readonly openCalculateButton = this.page.getByRole("button", { name: "生成建议" });

  // 二次确认弹窗确认按钮：确认/转采购共用。
  private readonly confirmExecuteButton = this.page.getByRole("button", { name: "确认执行" });

  // 补货页表格主体行：仅选可见表格，避免误选隐藏弹层中的行。
  private readonly tableRows = this.page.locator(".el-table__body-wrapper:visible tbody tr");

  // 按“行内包含计划号”定位目标补货计划。
  private rowByPlanNo(planNo: string): Locator {
    return this.tableRows.filter({ hasText: planNo }).first();
  }

  // 获取重算弹窗主体（可见态）。
  private recalculateDialog(): Locator {
    return this.page.locator(".el-dialog:visible", { hasText: "重算参数" }).first();
  }

  // 获取详情弹窗主体（可见态）。
  private detailDialog(): Locator {
    return this.page.locator(".el-dialog:visible", { hasText: "补货建议详情" }).first();
  }

  // 校验页面关键入口可见，确保当前已进入智能补货页。
  async assertLoaded(): Promise<void> {
    await expect(
      this.page.getByText("M6 说明：本页用于查看并管理智能补货建议，具备权限时可执行重算、确认与生成采购草稿。")
    ).toBeVisible();
    await expect(this.openCalculateButton).toBeVisible();
    await expect(this.keywordInput).toBeVisible();
  }

  // 按计划号查询并返回目标行，供后续状态流转动作复用。
  async searchRowByPlanNo(planNo: string): Promise<Locator> {
    await this.keywordInput.fill(planNo);
    await this.queryButton.click();
    const row = this.rowByPlanNo(planNo);
    await expect(row).toBeVisible();
    return row;
  }

  // 执行重算动作：待确认状态下调整参数并触发重算。
  async recalculateByPlanNo(planNo: string, payload: RecalculatePayload): Promise<void> {
    const row = await this.searchRowByPlanNo(planNo);
    await row.getByRole("button", { name: "重算" }).click();

    const dialog = this.recalculateDialog();
    await expect(dialog).toBeVisible();

    const numberInputs = dialog.locator(".el-input-number .el-input__inner");
    await numberInputs.nth(0).fill(String(payload.calcDays));
    await numberInputs.nth(1).fill(String(payload.leadTimeDays));
    await numberInputs.nth(2).fill(String(payload.safetyDays));
    await dialog.getByPlaceholder("例如：促销备货、参数临时调整原因").fill(payload.remark);

    await dialog.getByRole("button", { name: "执行重算" }).click();
    await expect(dialog).toBeHidden();

    // 重算成功后会自动弹出详情页，关闭后继续后续状态机动作。
    const detailDialog = this.detailDialog();
    await expect(detailDialog).toBeVisible();
    await detailDialog.locator(".el-dialog__headerbtn").click();
    await expect(detailDialog).toBeHidden();
  }

  // 执行确认动作：待确认 -> 待转采购。
  async confirmByPlanNo(planNo: string): Promise<void> {
    const row = await this.searchRowByPlanNo(planNo);
    await row.getByRole("button", { name: "确认" }).click();
    await this.confirmExecuteButton.click();
    await assertToastVisible(this.page, "确认成功");
  }

  // 执行转采购草稿动作：待转采购 -> 已生成采购草稿。
  async toPurchaseDraftByPlanNo(planNo: string): Promise<void> {
    const row = await this.searchRowByPlanNo(planNo);
    await row.getByRole("button", { name: "生成采购草稿" }).click();
    await this.confirmExecuteButton.click();
    await assertToastVisible(this.page, "采购草稿生成成功");
  }

  // 断言目标计划在列表中的状态文案。
  async assertStatus(planNo: string, statusText: "待确认" | "待转采购" | "已生成采购草稿"): Promise<void> {
    const row = await this.searchRowByPlanNo(planNo);
    await expect(row.getByText(statusText)).toBeVisible();
  }

  // 断言目标计划行已经生成采购草稿号（pd+日期+流水）。
  async assertPurchaseDraftNoGenerated(planNo: string): Promise<void> {
    const row = await this.searchRowByPlanNo(planNo);
    await expect(row.getByText(/pd\d{8}\d{4}/)).toBeVisible();
  }
}
