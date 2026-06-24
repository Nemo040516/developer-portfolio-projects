/*
 * @file 速览索引
 * @summary 应用主壳层 Page Object，封装登录后顶部状态栏、侧边菜单与退出操作。
 * @core 1. 校验登录后壳层已加载
 * @core 2. 按文案切换侧边菜单并校验激活态
 * @core 3. 管理员菜单分组展开/收起控制
 * @core 4. 执行退出登录并回到登录页
 * @entry 先看：assertLoggedIn、openMenuGroup、selectMenuByLabel、logout
 * @deps 依赖：@playwright/test、前端/src/App.vue
 * @state 关键元素：header、username、menu-group、menu-item、退出按钮
 * @risk 高风险修改点：侧栏文案、Element Plus 菜单激活类名、分组折叠样式类名
 * @link 相关文件：前端/e2e/utils/auth.ts、前端/e2e/tests/smoke
 */
import { expect, type Locator, type Page } from "@playwright/test";

export class AppShellPage {
  constructor(private readonly page: Page) {}

  // 顶部“当前角色”提示是登录后壳层最稳定的存在性信号。
  private readonly roleBanner = this.page.getByText("当前角色：");

  // 顶部用户名区域用于确认当前登录账号身份。
  private readonly usernameLocator = this.page.locator(".username");

  // 顶部退出按钮是登录态可操作入口。
  private readonly logoutButton = this.page.getByRole("button", { name: "退出" });

  // 统一按侧边菜单文案定位菜单项，减少测试代码重复。
  private menuItemByLabel(label: string): Locator {
    return this.page.locator(".el-menu-item", { hasText: label });
  }

  // 统一按分组标题定位可折叠分组，用于管理员“治理/作业”切换。
  private menuGroupToggleByLabel(label: string): Locator {
    return this.page.locator(".menu-group-title.menu-group-toggle", { hasText: label });
  }

  // 断言主壳层已经可用，并可选校验当前用户名。
  async assertLoggedIn(expectedUsername?: string): Promise<void> {
    await expect(this.roleBanner).toBeVisible();
    await expect(this.logoutButton).toBeVisible();
    if (expectedUsername) {
      await expect(this.usernameLocator).toHaveText(expectedUsername);
    }
  }

  // 断言首页主标题可见，作为“首页加载成功”的直接证据。
  async assertDashboardTitle(title: string): Promise<void> {
    await expect(this.page.getByRole("heading", { name: title })).toBeVisible();
  }

  // 按角色文案校验顶部身份标签，确保登录后的角色识别正确。
  async assertRoleLabel(roleLabel: string): Promise<void> {
    await expect(this.page.getByText(`当前角色：${roleLabel}`)).toBeVisible();
  }

  // 按分组标题展开菜单；若已展开则不重复点击，降低抖动。
  async openMenuGroup(groupLabel: string): Promise<void> {
    const toggle = this.menuGroupToggleByLabel(groupLabel);
    await expect(toggle).toBeVisible();
    const arrow = toggle.locator(".menu-group-arrow");
    const isCollapsed = await arrow.evaluate((node) => node.classList.contains("menu-group-arrow-collapsed"));
    if (isCollapsed) {
      await toggle.click();
    }
  }

  // 通过文案点击菜单项，驱动页面切换。
  async selectMenuByLabel(label: string): Promise<void> {
    const menuItem = this.menuItemByLabel(label);
    await expect(menuItem).toBeVisible();
    await menuItem.click();
  }

  // 校验指定菜单项处于激活态，确认菜单切换已真正生效。
  async assertMenuActive(label: string): Promise<void> {
    await expect(this.menuItemByLabel(label)).toHaveClass(/is-active/);
  }

  // 执行退出操作，供“退出登录”场景复用。
  async logout(): Promise<void> {
    await this.logoutButton.click();
  }
}
