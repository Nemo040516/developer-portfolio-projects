/*
 * @file 速览索引
 * @summary 登录辅助工具，负责按角色快速登录并执行基础“成功登录态”断言。
 * @core 1. 从环境配置读取角色账号
 * @core 2. 复用 LoginPage 完成登录动作
 * @core 3. 断言登录后壳层、角色与账号信息正确
 * @entry 先看：loginAsRole
 * @deps 依赖：前端/e2e/utils/env.ts、前端/e2e/pages/login.page.ts、前端/e2e/pages/app-shell.page.ts
 * @state 关键参数：role、e2eEnv.accounts
 * @risk 高风险修改点：角色文案映射、登录成功判定规则
 * @link 相关文件：前端/e2e/fixtures/test.ts
 */
import { expect, type Page } from "@playwright/test";

import { AppShellPage } from "../pages/app-shell.page";
import { LoginPage } from "../pages/login.page";
import { getRoleAccount, type E2EEnvironment, type E2ERole } from "./env";

const roleLabelMap: Record<E2ERole, string> = {
  admin: "管理员",
  warehouse: "仓库员",
  purchaser: "采购员"
};

// 通过角色名执行登录，避免每条用例重复拼装账号密码。
export async function loginAsRole(page: Page, e2eEnv: E2EEnvironment, role: E2ERole): Promise<void> {
  const account = getRoleAccount(e2eEnv, role);
  const loginPage = new LoginPage(page);
  const appShellPage = new AppShellPage(page);

  await loginPage.goto();
  await loginPage.assertLoaded();
  await loginPage.login(account.username, account.password);

  // 登录成功后应用应进入主界面，登录页标题应消失且主壳层元素可见。
  await expect(page.getByRole("heading", { name: "智能仓库订货系统" })).not.toBeVisible();
  await appShellPage.assertLoggedIn(account.username);
  await appShellPage.assertRoleLabel(roleLabelMap[role]);
}
