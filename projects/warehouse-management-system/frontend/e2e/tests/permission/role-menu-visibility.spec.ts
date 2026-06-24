/*
 * @file 速览索引
 * @summary 权限矩阵用例：校验 admin/warehouse/purchaser 三角色菜单可见与不可见边界。
 * @core 1. 管理员菜单全量可见（含作业分组展开后菜单）
 * @core 2. 仓库员菜单应包含作业链路并排除用户/供应商治理项
 * @core 3. 采购员菜单应聚焦补货/预警/主数据并排除仓储作业链路
 * @entry 先看：管理员菜单矩阵、仓库员菜单矩阵、采购员菜单矩阵
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/pages/app-shell.page.ts、前端/e2e/utils/auth.ts
 * @state 关键断言：.el-menu-item 文案存在性
 * @risk 高风险修改点：MenuService 菜单码映射、前端菜单文案、管理员分组折叠策略
 * @link 相关文件：前端/src/App.vue
 */
import { expect, type Page } from "@playwright/test";

import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { loginAsRole } from "../../utils/auth";

// 断言指定菜单文案可见。
async function assertMenuVisible(page: Page, label: string): Promise<void> {
  await expect(page.locator(".el-menu-item", { hasText: label }).first()).toBeVisible();
}

// 断言指定菜单文案不存在（当前角色无权限）。
async function assertMenuHidden(page: Page, label: string): Promise<void> {
  await expect(page.locator(".el-menu-item", { hasText: label })).toHaveCount(0);
}

test.describe("permission-角色权限矩阵", () => {
  test("管理员应可见全量核心菜单", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);

    // 步骤1：登录管理员，先校验治理区菜单。
    await loginAsRole(page, e2eEnv, "admin");
    await assertMenuVisible(page, "治理看板");
    await assertMenuVisible(page, "用户管理");
    await assertMenuVisible(page, "库存预警");
    await assertMenuVisible(page, "智能补货");

    // 步骤2：展开作业分组后校验执行链路菜单。
    await appShellPage.openMenuGroup("作业");
    await assertMenuVisible(page, "采购入库");
    await assertMenuVisible(page, "上架管理");
    await assertMenuVisible(page, "出库管理");
    await assertMenuVisible(page, "库存台账");
    await assertMenuVisible(page, "库存盘点");
    await assertMenuVisible(page, "仓库管理");
    await assertMenuVisible(page, "库位管理");
    await assertMenuVisible(page, "商品管理");
    await assertMenuVisible(page, "供应商管理");
  });

  test("仓库员应可见仓储作业菜单并屏蔽用户与供应商菜单", async ({ page, e2eEnv }) => {
    // 步骤1：登录仓库员并校验允许菜单。
    await loginAsRole(page, e2eEnv, "warehouse");
    await assertMenuVisible(page, "首页看板");
    await assertMenuVisible(page, "采购入库");
    await assertMenuVisible(page, "上架管理");
    await assertMenuVisible(page, "出库管理");
    await assertMenuVisible(page, "库存台账");
    await assertMenuVisible(page, "库存预警");
    await assertMenuVisible(page, "库存盘点");
    await assertMenuVisible(page, "智能补货");
    await assertMenuVisible(page, "商品管理");
    await assertMenuVisible(page, "库位管理");
    await assertMenuVisible(page, "仓库管理");

    // 步骤2：校验仓库员不可见菜单。
    await assertMenuHidden(page, "用户管理");
    await assertMenuHidden(page, "供应商管理");
  });

  test("采购员应聚焦补货预警与主数据菜单", async ({ page, e2eEnv }) => {
    // 步骤1：登录采购员并校验允许菜单。
    await loginAsRole(page, e2eEnv, "purchaser");
    await assertMenuVisible(page, "首页看板");
    await assertMenuVisible(page, "智能补货");
    await assertMenuVisible(page, "库存预警");
    await assertMenuVisible(page, "供应商管理");
    await assertMenuVisible(page, "商品管理");

    // 步骤2：校验采购员不可见仓储作业和治理菜单。
    await assertMenuHidden(page, "用户管理");
    await assertMenuHidden(page, "采购入库");
    await assertMenuHidden(page, "上架管理");
    await assertMenuHidden(page, "出库管理");
    await assertMenuHidden(page, "库存台账");
    await assertMenuHidden(page, "库存盘点");
    await assertMenuHidden(page, "仓库管理");
    await assertMenuHidden(page, "库位管理");
  });
});
