/*
 * @file 速览索引
 * @summary Smoke 用例：验证应用壳层可访问且登录页基础元素可见。
 * @core 1. 打开根路径
 * @core 2. 校验登录页标题与输入控件
 * @core 3. 为后续里程碑 B 的登录成功/失败用例提供最小基线
 * @entry 先看：访问首页时应展示登录页基础元素
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/pages/login.page.ts
 * @state 关键断言：登录标题、账号输入框、密码输入框、登录按钮
 * @risk 高风险修改点：UI 文案或 placeholder 调整需要同步更新 Page Object
 * @link 相关文件：前端/e2e/playwright.config.ts、前端/src/App.vue
 */
import { test } from "../../fixtures/test";
import { LoginPage } from "../../pages/login.page";

test.describe("smoke-应用壳层基线", () => {
  test("访问首页时应展示登录页基础元素", async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.assertLoaded();
  });
});
