/*
 * @file 速览索引
 * @summary 状态机拦截用例：验证入库单在不同状态下操作按钮显隐是否符合业务约束。
 * @core 1. 草稿态仅允许编辑/提交/删除
 * @core 2. 已提交态仅允许确认入库
 * @core 3. 已入库态应进入只读（仅详情）
 * @entry 先看：assertActionVisible、assertActionHidden、入库单状态机应驱动操作按钮显隐
 * @deps 依赖：前端/e2e/pages/inbound.page.ts、前端/e2e/utils/inbound.ts、前端/e2e/utils/auth.ts
 * @state 关键数据：inboundNo、row status、行内操作按钮可见性
 * @risk 高风险修改点：按钮文案、状态文案、状态值与按钮显隐映射
 * @link 相关文件：前端/src/components/InboundPanel.vue、前端/e2e/tests/m1-m6/m2-inbound-submit-confirm.spec.ts
 */
import { expect, type Locator } from "@playwright/test";

import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { InboundPage } from "../../pages/inbound.page";
import { loginAsRole } from "../../utils/auth";
import { createInboundDraftByApi } from "../../utils/inbound";

// 断言行内指定操作按钮可见，确保当前状态允许该动作。
async function assertActionVisible(row: Locator, actionLabel: string): Promise<void> {
  await expect(row.getByRole("button", { name: actionLabel })).toBeVisible();
}

// 断言行内指定操作按钮不存在，确保状态机已拦截该动作。
async function assertActionHidden(row: Locator, actionLabel: string): Promise<void> {
  await expect(row.getByRole("button", { name: actionLabel })).toHaveCount(0);
}

test.describe("permission-状态机拦截", () => {
  test("入库单状态机应驱动操作按钮显隐", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);
    const inboundPage = new InboundPage(page);

    // 步骤1：管理员登录并进入采购入库页面。
    await loginAsRole(page, e2eEnv, "admin");
    await appShellPage.openMenuGroup("作业");
    await appShellPage.selectMenuByLabel("采购入库");
    await appShellPage.assertMenuActive("采购入库");
    await inboundPage.assertLoaded();

    // 步骤2：通过 API 前置创建草稿单，先校验草稿态操作可见性。
    const inboundNo = await createInboundDraftByApi(page);
    await inboundPage.assertStatus(inboundNo, "草稿");
    const draftRow = await inboundPage.searchRowByInboundNo(inboundNo);
    await assertActionVisible(draftRow, "编辑");
    await assertActionVisible(draftRow, "提交");
    await assertActionVisible(draftRow, "删除");
    await assertActionHidden(draftRow, "确认入库");

    // 步骤3：提交后应进入“仅允许确认”的状态。
    await inboundPage.submitByInboundNo(inboundNo);
    await inboundPage.assertStatus(inboundNo, "已提交");
    const submittedRow = await inboundPage.searchRowByInboundNo(inboundNo);
    await assertActionHidden(submittedRow, "编辑");
    await assertActionHidden(submittedRow, "提交");
    await assertActionHidden(submittedRow, "删除");
    await assertActionVisible(submittedRow, "确认入库");

    // 步骤4：确认入库后应进入终态，只保留详情查看能力。
    await inboundPage.confirmByInboundNo(inboundNo);
    await inboundPage.assertStatus(inboundNo, "已入库");
    const confirmedRow = await inboundPage.searchRowByInboundNo(inboundNo);
    await assertActionHidden(confirmedRow, "编辑");
    await assertActionHidden(confirmedRow, "提交");
    await assertActionHidden(confirmedRow, "删除");
    await assertActionHidden(confirmedRow, "确认入库");
  });
});

