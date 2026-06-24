/*
 * @file 速览索引
 * @summary Smoke 用例：校验管理员关键菜单可达（治理区 + 作业区）。
 * @core 1. 点击治理区“用户管理”并校验页面入口元素
 * @core 2. 展开作业分组后点击“采购入库”并校验入口元素
 * @core 3. 校验菜单激活态随切换更新
 * @entry 先看：管理员可达用户管理与采购入库菜单
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/pages/app-shell.page.ts
 * @state 关键断言：菜单激活态、用户页“新增用户”、入库页“新建入库单”
 * @risk 高风险修改点：菜单分组文案、入口按钮文案、分组折叠策略
 * @link 相关文件：前端/src/App.vue、前端/src/components/UserPanel.vue、前端/src/components/InboundPanel.vue
 */
import { expect } from "@playwright/test";

import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { loginAsRole } from "../../utils/auth";

test.describe("smoke-关键菜单可达", () => {
  test("管理员应可访问用户管理与采购入库菜单", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);

    // 步骤1：登录后切到治理区“用户管理”。
    await loginAsRole(page, e2eEnv, "admin");
    await appShellPage.selectMenuByLabel("用户管理");
    await appShellPage.assertMenuActive("用户管理");
    await expect(page.getByRole("button", { name: "新增用户" })).toBeVisible();

    // 步骤2：展开“作业”分组并切到“采购入库”。
    await appShellPage.openMenuGroup("作业");
    await appShellPage.selectMenuByLabel("采购入库");
    await appShellPage.assertMenuActive("采购入库");
    await expect(page.getByRole("button", { name: "新建入库单" })).toBeVisible();
  });
});
