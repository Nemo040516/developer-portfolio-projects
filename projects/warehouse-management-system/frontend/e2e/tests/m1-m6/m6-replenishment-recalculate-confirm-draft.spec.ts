/*
 * @file 速览索引
 * @summary M6 核心流程用例：覆盖补货计划“生成 -> 重算 -> 确认 -> 转采购草稿”完整链路。
 * @core 1. 登录管理员并进入智能补货页面
 * @core 2. 通过 API 前置生成待确认补货计划
 * @core 3. 在 UI 执行重算并保持待确认状态
 * @core 4. 在 UI 执行确认并流转到待转采购
 * @core 5. 在 UI 执行转采购草稿并校验草稿号生成
 * @entry 先看：管理员可完成补货建议重算确认转采购全链路
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/pages/replenishment.page.ts、前端/e2e/utils/replenishment.ts
 * @state 关键数据：planNo（后端生成计划号）
 * @risk 高风险修改点：按钮文案、状态文案、重算弹窗字段顺序、采购草稿号格式
 * @link 相关文件：前端/src/components/ReplenishmentPanel.vue、后端/src/main/java/com/wms/backend/replenishment/controller/ReplenishmentController.java
 */
import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { ReplenishmentPage } from "../../pages/replenishment.page";
import { loginAsRole } from "../../utils/auth";
import { createReplenishmentDraftByApi } from "../../utils/replenishment";

test.describe("m6-智能补货流程", () => {
  test("管理员可完成补货建议重算确认转采购全链路", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);
    const replenishmentPage = new ReplenishmentPage(page);

    // 步骤1：登录并切入“智能补货”页面。
    await loginAsRole(page, e2eEnv, "admin");
    await appShellPage.openMenuGroup("治理");
    await appShellPage.selectMenuByLabel("智能补货");
    await appShellPage.assertMenuActive("智能补货");
    await replenishmentPage.assertLoaded();

    // 步骤2：通过 API 前置生成待确认补货计划并断言初始状态。
    const draftPlan = await createReplenishmentDraftByApi(page);
    await replenishmentPage.assertStatus(draftPlan.planNo, "待确认");

    // 步骤3：执行重算（参数微调）并确认仍为待确认状态。
    await replenishmentPage.recalculateByPlanNo(draftPlan.planNo, {
      calcDays: Math.min(draftPlan.calcDays + 1, 90),
      leadTimeDays: Math.min(draftPlan.leadTimeDays + 1, 60),
      safetyDays: Math.min(draftPlan.safetyDays + 1, 30),
      remark: `e2e-m6-recalc-${Date.now()}`
    });
    await replenishmentPage.assertStatus(draftPlan.planNo, "待确认");

    // 步骤4：确认补货建议并校验流转为待转采购。
    await replenishmentPage.confirmByPlanNo(draftPlan.planNo);
    await replenishmentPage.assertStatus(draftPlan.planNo, "待转采购");

    // 步骤5：转采购草稿并校验最终状态与草稿号生成。
    await replenishmentPage.toPurchaseDraftByPlanNo(draftPlan.planNo);
    await replenishmentPage.assertStatus(draftPlan.planNo, "已生成采购草稿");
    await replenishmentPage.assertPurchaseDraftNoGenerated(draftPlan.planNo);
  });
});
