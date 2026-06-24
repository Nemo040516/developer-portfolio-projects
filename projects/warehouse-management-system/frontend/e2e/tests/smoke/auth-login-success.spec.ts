/*
 * @file 速览索引
 * @summary Smoke 用例：校验管理员使用正确凭据可完成登录并进入首页。
 * @core 1. 使用 admin 账号执行登录
 * @core 2. 断言出现“登录成功”消息
 * @core 3. 断言主壳层与首页看板标题可见
 * @entry 先看：管理员正确凭据登录后进入治理看板首页
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/pages/app-shell.page.ts
 * @state 关键断言：登录成功消息、当前角色、治理看板标题
 * @risk 高风险修改点：登录提示文案、首页主标题文案、角色标签文案
 * @link 相关文件：前端/src/App.vue、前端/src/components/DashboardPanel.vue
 */
import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { LoginPage } from "../../pages/login.page";
import { getRoleAccount } from "../../utils/env";
import { assertToastVisible } from "../../utils/feedback";

test.describe("smoke-登录成功", () => {
  test("管理员正确凭据登录后进入治理看板首页", async ({ page, e2eEnv }) => {
    const loginPage = new LoginPage(page);
    const appShellPage = new AppShellPage(page);
    const adminAccount = getRoleAccount(e2eEnv, "admin");

    // 步骤1：进入登录页并输入管理员账号。
    await loginPage.goto();
    await loginPage.assertLoaded();
    await loginPage.login(adminAccount.username, adminAccount.password);

    // 步骤2：确认成功提示出现，且顶部身份信息与首页标题正确。
    await assertToastVisible(page, "登录成功");
    await appShellPage.assertLoggedIn(adminAccount.username);
    await appShellPage.assertRoleLabel("管理员");
    await appShellPage.assertDashboardTitle("治理看板");
  });
});
