---
title: 前端 E2E 测试工程说明
created: 2026-03-05
updated: 2026-03-05
tags:
  - E2E
  - Playwright
  - 前端测试
---

# E2E 测试工程说明

## 1. 目录结构
- `e2e/playwright.config.ts`：全局配置（报告、重试、截图录像、webServer）。
- `e2e/tests/smoke/`：快速冒烟用例。
- `e2e/tests/m1-m6/`：核心业务链路（里程碑 C）。
- `e2e/tests/permission/`：权限矩阵与异常分支（里程碑 D）。
- `e2e/tests/regression/`：跨模块组合回归（夜间/慢速）。
- `e2e/fixtures/`：公共夹具。
- `e2e/pages/`：Page Object 封装。
- `e2e/utils/`：环境变量、登录辅助等工具。

## 2. 初始化
1. 安装浏览器：
   - `npm run e2e:install`
2. 复制环境模板（可选，建议）：
   - 复制 `前端/.env.e2e.example` 为 `前端/.env.e2e.local`
   - 按测试环境修改账号密码

## 3. 常用命令
- `npm run e2e:test`：执行全部 E2E。
- `npm run e2e:test:smoke`：仅执行 smoke。
- `npm run e2e:test:core`：仅执行核心流程（`m1-m6`）。
- `npm run e2e:test:headed`：有头模式调试。
- `npm run e2e:test:ui`：Playwright UI 模式。
- `npm run e2e:report`：打开最近一次 HTML 报告。

## 4. 运行约定
- 默认会自动启动前端 dev 服务（`http://localhost:5173`）。
- 登录相关 smoke 依赖后端 `http://localhost:8080` 可用。
- 如果你要连远端环境，请设置：
  - `E2E_BASE_URL=http://你的地址`
  - `E2E_SKIP_WEBSERVER=1`

## 5. 里程碑 B 已落地用例
- `smoke/app-shell-load.spec.ts`：登录页基础元素可见。
- `smoke/auth-login-success.spec.ts`：登录成功进入首页。
- `smoke/auth-login-failure.spec.ts`：错误密码登录失败。
- `smoke/home-dashboard-load.spec.ts`：登录后首页核心区块加载。
- `smoke/menu-reachability.spec.ts`：关键菜单可达（用户管理/采购入库）。
- `smoke/auth-logout.spec.ts`：退出登录并清理 token。

## 6. 关联跳转
- `[[前端/e2e/playwright.config.ts]]`
- `[[前端/.env.e2e.example]]`
- `[[文档/02-待办事项/E2E测试框架总计划]]`

## 7. 里程碑 C 已落地（首批）
- `m1-m6/m1-master-data-ready.spec.ts`：M1 主数据页面就绪（仓库/库位/SKU/供应商）。
- `m1-m6/m2-inbound-submit-confirm.spec.ts`：M2 入库创建（API前置） -> UI提交 -> UI确认。
- `m1-m6/m3-putaway-submit-confirm.spec.ts`：M3 上架创建（API前置） -> UI提交 -> UI确认。
- `m1-m6/m4-outbound-submit-confirm.spec.ts`：M4 出库创建（API前置） -> UI提交 -> UI确认。
- `m1-m6/m5-inventory-alert-rule-upsert.spec.ts`：M5 预警规则维护（API前置） -> UI规则断言 -> UI预警结果断言。
- `m1-m6/m5-stocktake-submit-confirm.spec.ts`：M5 盘点创建（API前置） -> UI提交 -> UI确认。
- `m1-m6/m6-replenishment-recalculate-confirm-draft.spec.ts`：M6 补货建议生成（API前置） -> UI重算 -> UI确认 -> UI转采购草稿。

## 8. 里程碑 D 已落地（首批）
- `permission/role-menu-visibility.spec.ts`：三角色菜单可见/不可见矩阵（admin/warehouse/purchaser）。
- `permission/role-operation-guard.spec.ts`：关键操作按钮权限守卫（智能补货、库存预警规则、商品维护）。

## 9. 里程碑 D 已落地（第二批）
- `permission/state-machine-guard.spec.ts`：入库单状态机拦截验证（草稿/已提交/已入库对应操作按钮显隐）。
- `permission/exception-feedback.spec.ts`：关键异常提示验证（入库缺少必填、预警阈值非法）。

## 10. 里程碑 E 已落地（CI 门禁）
- `.github/workflows/e2e-pr-gate.yml`：PR 门禁执行 `smoke + core`。
- `.github/workflows/e2e-nightly-full.yml`：夜间定时执行全量 E2E（UTC 18:00，即北京时间 02:00）。
- `scripts/e2e-ci-run.sh`：统一封装“后端启动 -> 健康检查 -> E2E执行 -> 进程清理”。
