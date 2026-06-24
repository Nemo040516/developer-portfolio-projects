/*
 * @file 速览索引
 * @summary Smoke 用例：校验登录后首页治理看板核心区块可加载。
 * @core 1. 复用角色登录夹具完成管理员登录
 * @core 2. 校验首页标题、刷新按钮、治理入口可见
 * @entry 先看：管理员登录后首页应加载治理看板主区块
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/utils/auth.ts
 * @state 关键断言：治理看板标题、刷新看板按钮、治理入口文案
 * @risk 高风险修改点：首页模块命名变更会影响断言稳定性
 * @link 相关文件：前端/src/components/DashboardPanel.vue
 */
import { expect } from "@playwright/test";

import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { loginAsRole } from "../../utils/auth";

test.describe("smoke-首页加载", () => {
  test("管理员登录后应加载治理看板核心区块", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);

    // 步骤1：登录进入系统首页。
    await loginAsRole(page, e2eEnv, "admin");

    // 步骤2：验证首页主要可视区块已渲染。
    await appShellPage.assertDashboardTitle("治理看板");
    await expect(page.getByRole("button", { name: "刷新看板" })).toBeVisible();
    await expect(page.getByText("治理入口")).toBeVisible();
  });
});
