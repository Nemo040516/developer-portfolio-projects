---
title: package.json 速览索引
created: 2026-03-05
updated: 2026-03-05
tags:
  - 前端
  - 配置索引
  - E2E
---

# package.json 速览索引

- 文件作用：定义前端工程依赖与 npm 运行脚本。
- 核心职责：
  - 管理 Vue/Vite/Element Plus 运行依赖。
  - 管理 Playwright E2E 相关开发依赖。
  - 暴露 `e2e:*` 系列命令。
- 关键入口：`scripts.e2e:test`、`scripts.e2e:test:smoke`、`scripts.e2e:test:core`、`scripts.e2e:report`。
- 关键依赖：`@playwright/test`、`dotenv`、`vite`。
- 阅读顺序：先看 `scripts`，再看 `devDependencies`。
- 高风险修改点：脚本参数中的 `-c e2e/playwright.config.ts`，改错会导致命令失效。
- 相关文件：
  - `[[前端/e2e/playwright.config.ts]]`
  - `[[前端/.env.e2e.example]]`
