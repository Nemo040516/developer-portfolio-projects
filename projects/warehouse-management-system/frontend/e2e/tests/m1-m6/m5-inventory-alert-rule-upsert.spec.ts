/*
 * @file 速览索引
 * @summary M5 核心流程用例：覆盖库存预警“规则维护 -> 预警列表命中”关键链路。
 * @core 1. 登录管理员并进入库存预警页面
 * @core 2. 通过 API 前置确保启用规则存在（存在则更新，不存在则新增）
 * @core 3. 在规则页签查询并断言目标规则可见且启用
 * @core 4. 在预警页签查询并断言目标预警记录可见
 * @entry 先看：管理员可完成预警规则维护与结果联动验证
 * @deps 依赖：前端/e2e/fixtures/test.ts、前端/e2e/pages/inventory-alert.page.ts、前端/e2e/utils/inventory-alert.ts
 * @state 关键数据：warehouseName、skuCode（API 前置返回）
 * @risk 高风险修改点：规则/预警查询输入框文案、规则接口字段、预警判定口径
 * @link 相关文件：前端/src/components/InventoryAlertPanel.vue、后端/src/main/java/com/wms/backend/inventory/controller/InventoryController.java
 */
import { test } from "../../fixtures/test";
import { AppShellPage } from "../../pages/app-shell.page";
import { InventoryAlertPage } from "../../pages/inventory-alert.page";
import { loginAsRole } from "../../utils/auth";
import { ensureInventoryAlertRuleByApi } from "../../utils/inventory-alert";

test.describe("m5-库存预警流程", () => {
  test("管理员可完成预警规则维护与结果联动验证", async ({ page, e2eEnv }) => {
    const appShellPage = new AppShellPage(page);
    const inventoryAlertPage = new InventoryAlertPage(page);

    // 步骤1：登录并切入“库存预警”页面。
    await loginAsRole(page, e2eEnv, "admin");
    await appShellPage.openMenuGroup("治理");
    await appShellPage.selectMenuByLabel("库存预警");
    await appShellPage.assertMenuActive("库存预警");
    await inventoryAlertPage.assertLoaded();

    // 步骤2：通过 API 前置确保目标规则存在且启用。
    const anchor = await ensureInventoryAlertRuleByApi(page);

    // 步骤3：在“预警规则”页签查询并断言规则可见且为启用状态。
    await inventoryAlertPage.searchRulesByKeyword(anchor.skuCode);
    await inventoryAlertPage.assertRuleVisible(anchor.warehouseName, anchor.skuCode);
    await inventoryAlertPage.assertRuleEnabled(anchor.warehouseName, anchor.skuCode);

    // 步骤4：在“预警列表”页签查询并断言该仓库+SKU命中预警结果。
    await inventoryAlertPage.searchAlertsByKeyword(anchor.skuCode);
    await inventoryAlertPage.assertAlertVisible(anchor.warehouseName, anchor.skuCode);
  });
});
