/*
 * @file 速览索引
 * @summary M1 页面维度主数据就绪用例：校验仓库/库位/SKU/供应商模块可达且列表有数据。
 * @core 1. 登录管理员并展开“作业”菜单分组
 * @core 2. 依次进入仓库、库位、商品、供应商页面
 * @core 3. 校验每个页面的关键入口按钮与列表至少一行数据
 * @entry 先看：管理员登录后主数据四模块均可加载且有基线数据
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/pages/app-shell.page.ts、前端/e2e/utils/auth.ts
 * @state 关键断言：新增按钮文案、表格行数
 * @risk 高风险修改点：菜单文案、按钮文案、分页空数据口径
 * @link 相关文件：前端/src/components/WarehousePanel.vue、前端/src/components/LocationPanel.vue、前端/src/components/SkuPanel.vue、前端/src/components/SupplierPanel.vue
 */
import { expect, type Page } from "@playwright/test";

import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { loginAsRole } from "../../utils/auth";

// 统一断言当前主数据页面有至少一条记录，避免重复编写表格选择器逻辑。
async function expectTableHasRows(page: Page): Promise<void> {
  const rows = page.locator(".el-table__body-wrapper tbody tr");
  await expect(rows.first()).toBeVisible();
  const rowCount = await rows.count();
  expect(rowCount).toBeGreaterThan(0);
}

// 统一执行“切菜单 + 断言入口按钮 + 断言列表有数据”步骤，保持四个主数据模块验收口径一致。
async function verifyMasterDataModule(
  appShellPage: AppShellPage,
  moduleLabel: string,
  createButtonLabel: string,
  page: Page
): Promise<void> {
  await appShellPage.selectMenuByLabel(moduleLabel);
  await appShellPage.assertMenuActive(moduleLabel);
  await expect(page.getByRole("button", { name: createButtonLabel })).toBeVisible();
  await expectTableHasRows(page);
}

test.describe("m1-主数据页面就绪", () => {
  test("管理员可加载仓库/库位/SKU/供应商主数据页面", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);

    // 步骤1：登录并展开管理员“作业”菜单分组，准备访问主数据模块。
    await loginAsRole(page, e2eEnv, "admin");
    await appShellPage.openMenuGroup("作业");

    // 步骤2：逐一校验主数据模块可达且列表有基线数据。
    await verifyMasterDataModule(appShellPage, "仓库管理", "新增仓库", page);
    await verifyMasterDataModule(appShellPage, "库位管理", "新增库位", page);
    await verifyMasterDataModule(appShellPage, "商品管理", "新增商品", page);
    await verifyMasterDataModule(appShellPage, "供应商管理", "新增供应商", page);
  });
});
