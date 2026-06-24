/*
 * @file 速览索引
 * @summary 权限操作守卫用例：校验关键页面按钮在不同角色下的可操作性边界。
 * @core 1. 校验智能补货“生成建议”按钮在仓库员/采购员角色下的差异
 * @core 2. 校验库存预警“预警规则”页签在仓库员/采购员角色下的差异
 * @core 3. 校验商品页“新增商品”按钮在仓库员/采购员角色下的差异
 * @entry 先看：智能补货操作权限差异、库存预警规则页签差异、商品维护权限差异
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/pages/app-shell.page.ts、前端/e2e/utils/auth.ts
 * @state 关键断言：角色提示文案、按钮可见性、页签存在性
 * @risk 高风险修改点：角色权限口径、按钮文案、页签文案
 * @link 相关文件：前端/src/components/ReplenishmentPanel.vue、前端/src/components/InventoryAlertPanel.vue、前端/src/components/SkuPanel.vue
 */
import { expect } from "@playwright/test";

import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { loginAsRole } from "../../utils/auth";

test.describe("permission-关键操作守卫", () => {
  test("仓库员在智能补货应为只读，采购员应可操作", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);

    // 步骤1：仓库员进入智能补货，断言只读提示与操作按钮隐藏。
    await loginAsRole(page, e2eEnv, "warehouse");
    await appShellPage.selectMenuByLabel("智能补货");
    await appShellPage.assertMenuActive("智能补货");
    await expect(page.getByText("当前为只读模式，仅可查询与查看补货建议详情。")).toBeVisible();
    await expect(page.getByRole("button", { name: "生成建议" })).toHaveCount(0);
    await appShellPage.logout();

    // 步骤2：采购员进入智能补货，断言可操作提示与“生成建议”按钮可见。
    await loginAsRole(page, e2eEnv, "purchaser");
    await appShellPage.selectMenuByLabel("智能补货");
    await appShellPage.assertMenuActive("智能补货");
    await expect(page.getByText("可执行生成建议、重算、确认与生成采购草稿操作。")).toBeVisible();
    await expect(page.getByRole("button", { name: "生成建议" })).toBeVisible();
  });

  test("库存预警规则页签应仅对管理员与仓库员开放", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);

    // 步骤1：采购员访问库存预警，规则页签与新增规则按钮应隐藏。
    await loginAsRole(page, e2eEnv, "purchaser");
    await appShellPage.selectMenuByLabel("库存预警");
    await appShellPage.assertMenuActive("库存预警");
    await expect(page.getByText("当前为只读模式，仅可查看预警列表。预警规则由管理员或仓库员维护。")).toBeVisible();
    await expect(page.getByRole("tab", { name: "预警规则" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "新增规则" })).toHaveCount(0);
    await appShellPage.logout();

    // 步骤2：仓库员访问库存预警，规则页签与新增规则按钮应可见。
    await loginAsRole(page, e2eEnv, "warehouse");
    await appShellPage.selectMenuByLabel("库存预警");
    await appShellPage.assertMenuActive("库存预警");
    await expect(page.getByRole("tab", { name: "预警规则" })).toBeVisible();
    await page.getByRole("tab", { name: "预警规则" }).click();
    await expect(page.getByRole("button", { name: "新增规则" })).toBeVisible();
  });

  test("商品主数据维护按钮应仅对管理员与仓库员开放", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);

    // 步骤1：采购员进入商品页，断言只读提示与新增按钮隐藏。
    await loginAsRole(page, e2eEnv, "purchaser");
    await appShellPage.selectMenuByLabel("商品管理");
    await appShellPage.assertMenuActive("商品管理");
    await expect(page.getByText("当前为只读模式，仅可查看商品信息。")).toBeVisible();
    await expect(page.getByRole("button", { name: "新增商品" })).toHaveCount(0);
    await appShellPage.logout();

    // 步骤2：仓库员进入商品页，断言新增按钮可见。
    await loginAsRole(page, e2eEnv, "warehouse");
    await appShellPage.selectMenuByLabel("商品管理");
    await appShellPage.assertMenuActive("商品管理");
    await expect(page.getByRole("button", { name: "新增商品" })).toBeVisible();
  });
});
