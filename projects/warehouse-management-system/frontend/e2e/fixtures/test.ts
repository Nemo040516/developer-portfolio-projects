/*
 * @file 速览索引
 * @summary Playwright 自定义夹具入口，负责向每条用例注入统一的 E2E 环境配置。
 * @core 1. 扩展基础 test 夹具
 * @core 2. 注入 e2eEnv（角色账号 + baseURL）
 * @core 3. 统一导出 expect，减少重复导入
 * @entry 先看：E2EFixtures、test.extend、e2eEnv fixture
 * @deps 依赖：@playwright/test、前端/e2e/utils/env.ts
 * @state 关键数据：e2eEnv
 * @risk 高风险修改点：fixture 名称变更会导致全部用例参数失效
 * @link 相关文件：前端/e2e/tests/smoke/app-shell-load.spec.ts
 */
import { expect, test as base } from "@playwright/test";

import { loadE2EEnvironment, type E2EEnvironment } from "../utils/env";

type E2EFixtures = {
  e2eEnv: E2EEnvironment;
};

export const test = base.extend<E2EFixtures>({
  // 在每条用例执行前准备一份环境快照，保证配置读取逻辑集中维护。
  e2eEnv: async ({}, use) => {
    const e2eEnv = loadE2EEnvironment();
    await use(e2eEnv);
  }
});

export { expect };
