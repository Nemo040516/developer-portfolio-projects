/*
 * @file 速览索引
 * @summary M3 核心流程用例：覆盖上架单“创建 -> 提交 -> 确认上架”完整链路。
 * @core 1. 登录管理员并进入上架管理页面
 * @core 2. 通过 API 前置创建可上架草稿单
 * @core 3. 执行提交并断言状态变为已提交
 * @core 4. 执行确认上架并断言状态变为已完成
 * @entry 先看：管理员可完成上架创建提交确认全链路
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/pages/putaway.page.ts、前端/e2e/utils/putaway.ts
 * @state 关键数据：putawayNo（后端生成单号）
 * @risk 高风险修改点：状态文案、确认弹窗文案、上架创建接口字段
 * @link 相关文件：前端/src/components/PutawayPanel.vue、后端/src/main/java/com/wms/backend/putaway/controller/PutawayController.java
 */
import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { PutawayPage } from "../../pages/putaway.page";
import { loginAsRole } from "../../utils/auth";
import { createPutawayDraftByApi } from "../../utils/putaway";

test.describe("m3-上架流程", () => {
  test("管理员可完成上架创建提交确认全链路", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);
    const putawayPage = new PutawayPage(page);

    // 步骤1：登录并切入“上架管理”页面。
    await loginAsRole(page, e2eEnv, "admin");
    await appShellPage.openMenuGroup("作业");
    await appShellPage.selectMenuByLabel("上架管理");
    await appShellPage.assertMenuActive("上架管理");
    await putawayPage.assertLoaded();

    // 步骤2：通过 API 前置创建草稿上架单并校验初始状态。
    const putawayNo = await createPutawayDraftByApi(page);
    await putawayPage.assertStatus(putawayNo, "草稿");

    // 步骤3：提交上架单并校验状态流转为“已提交”。
    await putawayPage.submitByPutawayNo(putawayNo);
    await putawayPage.assertStatus(putawayNo, "已提交");

    // 步骤4：确认上架并校验最终状态为“已完成”。
    await putawayPage.confirmByPutawayNo(putawayNo);
    await putawayPage.assertStatus(putawayNo, "已完成");
  });
});
