/*
 * @file 速览索引
 * @summary 页面反馈断言工具，负责统一校验 Element Plus 全局消息提示（成功/失败文案）。
 * @core 1. 按文案定位消息提示
 * @core 2. 统一可见性等待超时
 * @entry 先看：assertToastVisible
 * @deps 依赖：@playwright/test、Element Plus Message DOM 结构
 * @state 关键元素：.el-message__content
 * @risk 高风险修改点：UI 库升级后消息 DOM 结构变化会导致定位失效
 * @link 相关文件：前端/e2e/tests/smoke/auth-login-success.spec.ts
 */
import { expect, type Page } from "@playwright/test";

// 统一断言消息提示可见，避免各测试重复编写选择器与超时参数。
export async function assertToastVisible(page: Page, text: string): Promise<void> {
  await expect(page.locator(".el-message__content", { hasText: text }).last()).toBeVisible({ timeout: 10_000 });
}
