/*
 * @file 速览索引
 * @summary M4 核心流程用例：覆盖出库单“创建 -> 提交 -> 确认出库”完整链路。
 * @core 1. 登录管理员并进入出库管理页面
 * @core 2. 通过 API 前置创建可出库草稿单
 * @core 3. 执行提交并断言状态变为已提交
 * @core 4. 执行确认出库并断言状态变为已完成
 * @entry 先看：管理员可完成出库创建提交确认全链路
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/pages/outbound.page.ts、前端/e2e/utils/outbound.ts
 * @state 关键数据：outboundNo（后端生成单号）
 * @risk 高风险修改点：状态文案、确认弹窗文案、出库创建接口字段
 * @link 相关文件：前端/src/components/OutboundPanel.vue、后端/src/main/java/com/wms/backend/outbound/controller/OutboundController.java
 */
import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { OutboundPage } from "../../pages/outbound.page";
import { loginAsRole } from "../../utils/auth";
import { createOutboundDraftByApi } from "../../utils/outbound";

test.describe("m4-出库流程", () => {
  test("管理员可完成出库创建提交确认全链路", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);
    const outboundPage = new OutboundPage(page);

    // 步骤1：登录并切入“出库管理”页面。
    await loginAsRole(page, e2eEnv, "admin");
    await appShellPage.openMenuGroup("作业");
    await appShellPage.selectMenuByLabel("出库管理");
    await appShellPage.assertMenuActive("出库管理");
    await outboundPage.assertLoaded();

    // 步骤2：通过 API 前置创建草稿出库单并校验初始状态。
    const outboundNo = await createOutboundDraftByApi(page);
    await outboundPage.assertStatus(outboundNo, "草稿");

    // 步骤3：提交出库单并校验状态流转为“已提交”。
    await outboundPage.submitByOutboundNo(outboundNo);
    await outboundPage.assertStatus(outboundNo, "已提交");

    // 步骤4：确认出库并校验最终状态为“已完成”。
    await outboundPage.confirmByOutboundNo(outboundNo);
    await outboundPage.assertStatus(outboundNo, "已完成");
  });
});
