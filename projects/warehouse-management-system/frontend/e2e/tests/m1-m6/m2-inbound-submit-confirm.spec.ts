/*
 * @file 速览索引
 * @summary M2 核心流程用例：覆盖入库单“创建 -> 提交 -> 确认入库”完整链路。
 * @core 1. 登录管理员并进入采购入库页面
 * @core 2. 通过 API 前置创建最小可用草稿单并获取单号
 * @core 3. 执行提交并断言状态变为已提交
 * @core 4. 执行确认入库并断言状态变为已入库
 * @entry 先看：管理员可完成入库创建提交确认全链路
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/pages/inbound.page.ts、前端/e2e/utils/auth.ts、前端/e2e/utils/inbound.ts
 * @state 关键数据：inboundNo（后端生成单号）
 * @risk 高风险修改点：按钮文案、状态文案、对话框确认文案、入库创建接口字段
 * @link 相关文件：前端/src/components/InboundPanel.vue、后端/src/main/java/com/wms/backend/inbound/controller/InboundController.java
 */
import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { InboundPage } from "../../pages/inbound.page";
import { loginAsRole } from "../../utils/auth";
import { createInboundDraftByApi } from "../../utils/inbound";

test.describe("m2-入库流程", () => {
  test("管理员可完成入库创建提交确认全链路", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);
    const inboundPage = new InboundPage(page);

    // 步骤1：登录并切入“采购入库”页面。
    await loginAsRole(page, e2eEnv, "admin");
    await appShellPage.openMenuGroup("作业");
    await appShellPage.selectMenuByLabel("采购入库");
    await appShellPage.assertMenuActive("采购入库");
    await inboundPage.assertLoaded();

    // 步骤2：通过 API 前置创建草稿入库单并确认状态为“草稿”。
    const inboundNo = await createInboundDraftByApi(page);
    await inboundPage.assertStatus(inboundNo, "草稿");

    // 步骤3：提交入库单并确认状态流转为“已提交”。
    await inboundPage.submitByInboundNo(inboundNo);
    await inboundPage.assertStatus(inboundNo, "已提交");

    // 步骤4：确认入库并校验最终状态为“已入库”。
    await inboundPage.confirmByInboundNo(inboundNo);
    await inboundPage.assertStatus(inboundNo, "已入库");
  });
});
