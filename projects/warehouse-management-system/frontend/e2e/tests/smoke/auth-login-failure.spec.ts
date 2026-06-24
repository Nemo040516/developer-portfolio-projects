/*
 * @file 速览索引
 * @summary Smoke 用例：校验管理员密码错误时登录失败并停留在登录页。
 * @core 1. 使用 admin 账号 + 错误密码尝试登录
 * @core 2. 断言出现“账号或密码错误”提示
 * @core 3. 断言页面仍处于未登录态
 * @entry 先看：管理员错误密码登录应失败且不进入主壳层
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/pages/login.page.ts
 * @state 关键断言：失败提示、登录表单可见、退出按钮不可见
 * @risk 高风险修改点：后端错误文案口径、登录失败交互策略
 * @link 相关文件：后端/src/main/java/com/wms/backend/auth/service/AuthService.java
 */
import { expect } from "@playwright/test";

import { test } from "../../fixtures/test";
import { LoginPage } from "../../pages/login.page";
import { getRoleAccount } from "../../utils/env";
import { assertToastVisible } from "../../utils/feedback";

test.describe("smoke-登录失败", () => {
  test("管理员密码错误时应提示失败并停留登录页", async ({ page, e2eEnv }) => {
    const loginPage = new LoginPage(page);
    const adminAccount = getRoleAccount(e2eEnv, "admin");
    const wrongPassword = `${adminAccount.password}_wrong`;

    // 步骤1：使用错误密码尝试登录。
    await loginPage.goto();
    await loginPage.assertLoaded();
    await loginPage.login(adminAccount.username, wrongPassword);

    // 步骤2：验证失败提示与页面状态，确保未误入登录后壳层。
    await assertToastVisible(page, "账号或密码错误");
    await loginPage.assertLoaded();
    await expect(page.getByRole("button", { name: "退出" })).toHaveCount(0);
  });
});
