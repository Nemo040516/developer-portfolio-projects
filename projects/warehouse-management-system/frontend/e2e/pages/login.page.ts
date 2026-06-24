/*
 * @file 速览索引
 * @summary 登录页 Page Object，封装登录页常用定位器和交互动作，降低用例重复代码。
 * @core 1. 进入登录页
 * @core 2. 校验登录页关键元素可见
 * @core 3. 执行账号密码登录动作
 * @entry 先看：goto、assertLoaded、login
 * @deps 依赖：@playwright/test、前端/src/App.vue（登录 UI）
 * @state 关键元素：标题、账号输入框、密码输入框、登录按钮
 * @risk 高风险修改点：按钮文案与 placeholder 变更会影响选择器稳定性
 * @link 相关文件：前端/e2e/tests/smoke/app-shell-load.spec.ts、前端/e2e/utils/auth.ts
 */
import { expect, type Page } from "@playwright/test";

export class LoginPage {
  constructor(private readonly page: Page) {}

  // 统一用根路径进入应用壳层，保持用例入口一致。
  async goto(): Promise<void> {
    await this.page.goto("/");
  }

  // 校验登录页关键控件是否渲染，作为 smoke 级最小可用断言。
  async assertLoaded(): Promise<void> {
    await expect(this.page.getByRole("heading", { name: "智能仓库订货系统" })).toBeVisible();
    await expect(this.page.getByPlaceholder("请输入账号")).toBeVisible();
    await expect(this.page.getByPlaceholder("请输入密码")).toBeVisible();
    await expect(this.page.getByRole("button", { name: "登录" })).toBeVisible();
  }

  // 执行标准登录动作，供后续里程碑 B/C 的业务链路用例复用。
  async login(username: string, password: string): Promise<void> {
    await this.page.getByPlaceholder("请输入账号").fill(username);
    await this.page.getByPlaceholder("请输入密码").fill(password);
    await this.page.getByRole("button", { name: "登录" }).click();
  }
}
