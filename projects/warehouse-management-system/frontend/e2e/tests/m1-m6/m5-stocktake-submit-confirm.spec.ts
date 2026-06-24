/*
 * @file 速览索引
 * @summary M5 核心流程用例：覆盖盘点单“创建 -> 提交 -> 确认盘点”完整链路。
 * @core 1. 登录管理员并进入库存盘点页面
 * @core 2. 通过 API 前置创建可提交草稿盘点单
 * @core 3. 执行提交并断言状态变为已提交
 * @core 4. 执行确认盘点并断言状态变为已完成
 * @entry 先看：管理员可完成盘点创建提交确认全链路
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/pages/stocktake.page.ts、前端/e2e/utils/stocktake.ts
 * @state 关键数据：stocktakeNo（后端生成单号）
 * @risk 高风险修改点：状态文案、确认弹窗文案、盘点创建接口字段
 * @link 相关文件：前端/src/components/StocktakePanel.vue、后端/src/main/java/com/wms/backend/stocktake/controller/StocktakeController.java
 */
import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { StocktakePage } from "../../pages/stocktake.page";
import { loginAsRole } from "../../utils/auth";
import { createStocktakeDraftByApi } from "../../utils/stocktake";

test.describe("m5-库存盘点流程", () => {
  test("管理员可完成盘点创建提交确认全链路", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);
    const stocktakePage = new StocktakePage(page);

    // 步骤1：登录并切入“库存盘点”页面。
    await loginAsRole(page, e2eEnv, "admin");
    await appShellPage.openMenuGroup("作业");
    await appShellPage.selectMenuByLabel("库存盘点");
    await appShellPage.assertMenuActive("库存盘点");
    await stocktakePage.assertLoaded();

    // 步骤2：通过 API 前置创建草稿盘点单并校验初始状态。
    const stocktakeNo = await createStocktakeDraftByApi(page);
    await stocktakePage.assertStatus(stocktakeNo, "草稿");

    // 步骤3：提交盘点单并校验状态流转为“已提交”。
    await stocktakePage.submitByStocktakeNo(stocktakeNo);
    await stocktakePage.assertStatus(stocktakeNo, "已提交");

    // 步骤4：确认盘点并校验最终状态为“已完成”。
    await stocktakePage.confirmByStocktakeNo(stocktakeNo);
    await stocktakePage.assertStatus(stocktakeNo, "已完成");
  });
});
