/*
 * @file 速览索引
 * @summary 异常反馈用例：验证关键表单校验失败时前端是否给出正确提示文案。
 * @core 1. 入库新建缺少供应商/仓库时提示
 * @core 2. 预警规则阈值非法时提示
 * @entry 先看：selectFirstOptionByFormLabel、fillInputNumberByFormLabel、关键异常提示应可见且准确
 * @deps 依赖：前端/e2e/pages/app-shell.page.ts、前端/e2e/pages/inbound.page.ts、前端/e2e/utils/auth.ts、前端/e2e/utils/feedback.ts
 * @state 关键元素：新建入库单弹窗、新增预警规则弹窗、全局消息提示
 * @risk 高风险修改点：表单标签文案、按钮文案、ElMessage 提示文案
 * @link 相关文件：前端/src/components/InboundPanel.vue、前端/src/components/InventoryAlertPanel.vue
 */
import { expect, type Page } from "@playwright/test";

import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { InboundPage } from "../../pages/inbound.page";
import { loginAsRole } from "../../utils/auth";
import { assertToastVisible } from "../../utils/feedback";

// 在弹窗表单中按标签选择第一个可用下拉选项，供仓库/SKU等字段复用。
async function selectFirstOptionByFormLabel(page: Page, labelText: string): Promise<void> {
  const formItem = page.locator(".el-dialog .el-form-item", { hasText: labelText }).first();
  await expect(formItem).toBeVisible();
  await formItem.locator(".el-select").first().click();
  const visibleDropdown = page.locator(".el-select-dropdown:visible").last();
  const firstEnabledOption = visibleDropdown.locator(".el-select-dropdown__item:not(.is-disabled)").first();
  await expect(firstEnabledOption).toBeVisible();
  // Element Plus 下拉项有过渡动画，原生 click 更稳定。
  await firstEnabledOption.evaluate((node) => (node as HTMLElement).click());
}

// 在弹窗表单中按标签填写数字输入框，供阈值字段复用。
async function fillInputNumberByFormLabel(page: Page, labelText: string, value: number): Promise<void> {
  const formItem = page.locator(".el-dialog .el-form-item", { hasText: labelText }).first();
  const input = formItem.locator(".el-input-number input").first();
  await expect(input).toBeVisible();
  await input.fill(String(value));
  await input.press("Tab");
}

test.describe("permission-关键异常反馈", () => {
  test("入库新建缺少供应商与仓库时应提示校验失败", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);
    const inboundPage = new InboundPage(page);

    // 步骤1：管理员登录并进入采购入库页。
    await loginAsRole(page, e2eEnv, "admin");
    await appShellPage.openMenuGroup("作业");
    await appShellPage.selectMenuByLabel("采购入库");
    await appShellPage.assertMenuActive("采购入库");
    await inboundPage.assertLoaded();

    // 步骤2：打开新建弹窗，不填供应商/仓库直接保存，断言提示文案。
    await page.getByRole("button", { name: "新建入库单" }).click();
    await expect(page.getByRole("heading", { name: "新建入库单" })).toBeVisible();
    await page.getByRole("button", { name: "保存" }).click();
    await assertToastVisible(page, "请选择供应商和仓库");
  });

  test("预警规则阈值不满足排序约束时应提示非法", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);

    // 步骤1：管理员登录并进入库存预警规则页。
    await loginAsRole(page, e2eEnv, "admin");
    await appShellPage.selectMenuByLabel("库存预警");
    await appShellPage.assertMenuActive("库存预警");
    await page.getByRole("tab", { name: "预警规则" }).click();
    await expect(page.getByRole("button", { name: "新增规则" })).toBeVisible();

    // 步骤2：打开新增规则，填充合法仓库/SKU但故意设置非法阈值区间。
    await page.getByRole("button", { name: "新增规则" }).click();
    await expect(page.getByRole("heading", { name: "新增预警规则" })).toBeVisible();
    await selectFirstOptionByFormLabel(page, "仓库");
    await selectFirstOptionByFormLabel(page, "SKU");
    await fillInputNumberByFormLabel(page, "预警下限", 10);
    await fillInputNumberByFormLabel(page, "安全库存", 5);
    await fillInputNumberByFormLabel(page, "预警上限", 20);

    // 步骤3：点击保存并断言前端校验提示准确可见。
    await page.getByRole("button", { name: "保存" }).click();
    await assertToastVisible(page, "阈值需满足 预警下限 <= 安全库存 <= 预警上限");
  });
});

