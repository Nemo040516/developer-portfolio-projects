/*
 * @file 速览索引
 * @summary Smoke 用例：校验退出登录可回到登录页并清理本地 token。
 * @core 1. 登录后执行退出操作
 * @core 2. 断言回到登录页
 * @core 3. 断言 sessionStorage/localStorage token 被清理
 * @entry 先看：管理员退出登录后应回到登录页且无残留 token
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/utils/auth.ts
 * @state 关键断言：登录页可见、wms_token 为空
 * @risk 高风险修改点：会话存储策略变更（session/local）会影响断言
 * @link 相关文件：前端/src/App.vue、前端/src/api/http.js
 */
import { expect } from "@playwright/test";

import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { LoginPage } from "../../pages/login.page";
import { loginAsRole } from "../../utils/auth";

test.describe("smoke-退出登录", () => {
  test("管理员退出后应返回登录页并清空 token", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);
    const loginPage = new LoginPage(page);

    // 步骤1：登录进入系统，并执行退出。
    await loginAsRole(page, e2eEnv, "admin");
    await appShellPage.logout();

    // 步骤2：确认回到登录页。
    await loginPage.assertLoaded();

    // 步骤3：确认本地登录凭据已被清理。
    const sessionToken = await page.evaluate(() => sessionStorage.getItem("wms_token"));
    const localToken = await page.evaluate(() => localStorage.getItem("wms_token"));
    expect(sessionToken).toBeNull();
    expect(localToken).toBeNull();
  });
});
