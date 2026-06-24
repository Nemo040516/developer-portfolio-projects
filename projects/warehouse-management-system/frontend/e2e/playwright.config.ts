/*
 * @file 速览索引
 * @summary Playwright 全局配置，负责 E2E 测试入口、失败证据采集与本地服务联动。
 * @core 1. 加载 .env.e2e / .env.e2e.local 环境变量
 * @core 2. 统一截图、录像、trace 与报告输出目录
 * @core 3. 配置重试、并行度与 Chromium 项目
 * @core 4. 控制本地 webServer 自动启动与复用
 * @entry 先看：dotenv.config、shouldUseManagedWebServer、defineConfig
 * @deps 依赖：@playwright/test、dotenv、前端/.env.e2e
 * @state 关键变量：E2E_BASE_URL、E2E_SKIP_WEBSERVER、CI
 * @risk 高风险修改点：webServer.command、baseURL、retries、outputDir
 * @link 相关文件：前端/e2e/utils/env.ts、前端/e2e/tests/smoke/app-shell-load.spec.ts
 */
import path from "node:path";
import { fileURLToPath } from "node:url";

import dotenv from "dotenv";
import { defineConfig, devices } from "@playwright/test";

// 解析当前配置文件目录，避免在不同工作目录执行时路径失效。
const configFilePath = fileURLToPath(import.meta.url);
const e2eRootDir = path.dirname(configFilePath);
const frontendRootDir = path.resolve(e2eRootDir, "..");

// 约定本地默认访问地址，保持与后端 CORS 默认白名单一致（localhost:5173）。
const defaultBaseURL = "http://localhost:5173";

// 先加载共享配置，再加载本地私有覆盖配置（local 覆盖同名变量）。
dotenv.config({ path: path.join(frontendRootDir, ".env.e2e"), quiet: true });
dotenv.config({ path: path.join(frontendRootDir, ".env.e2e.local"), override: true, quiet: true });

// 读取核心运行参数：目标地址与是否跳过内置 webServer。
const baseURL = process.env.E2E_BASE_URL || defaultBaseURL;
const shouldSkipWebServer = process.env.E2E_SKIP_WEBSERVER === "1";

// 仅在“未显式跳过 + 使用默认本地地址”时，自动管理前端开发服务器。
const shouldUseManagedWebServer = !shouldSkipWebServer && baseURL === defaultBaseURL;

export default defineConfig({
  testDir: path.join(e2eRootDir, "tests"),
  outputDir: path.join(e2eRootDir, "test-results"),
  fullyParallel: true,
  timeout: 60_000,
  expect: {
    timeout: 10_000
  },
  retries: process.env.CI ? 2 : 1,
  workers: process.env.CI ? 2 : undefined,
  reporter: [
    ["list"],
    ["html", { outputFolder: path.join(e2eRootDir, "playwright-report"), open: "never" }]
  ],
  use: {
    baseURL,
    headless: true,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 10_000,
    navigationTimeout: 20_000,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure"
  },
  webServer: shouldUseManagedWebServer
    ? {
        // 使用 localhost 启动，避免与后端 CORS 白名单不一致导致 403。
        command: "npm run dev -- --host localhost --port 5173",
        url: defaultBaseURL,
        cwd: frontendRootDir,
        timeout: 120_000,
        reuseExistingServer: !process.env.CI
      }
    : undefined,
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"]
      }
    }
  ]
});
