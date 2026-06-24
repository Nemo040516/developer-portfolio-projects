# Warehouse Management System Frontend / 仓储管理系统前端

## 中文说明

这是仓储管理系统的 Vue 3 + Vite 前端部分，属于毕设级作品集项目的一部分。

## English

This is the Vue 3 + Vite frontend for the warehouse management system. It is part of a graduation-project-level portfolio case.

## Requirements / 环境要求

- Node.js 18+
- npm 9+

## Run / 运行

```powershell
npm install
npm run dev
```

Default local URL / 默认本地地址：

```text
http://localhost:5173
```

The Vite proxy forwards `/api` requests to `http://localhost:8080`.

Vite 代理会将 `/api` 请求转发到 `http://localhost:8080`。

## Implemented Areas / 已实现内容

- login page and session token handling / 登录页和会话 token 处理
- role-based menu visibility / 按角色显示菜单
- dashboard shell / 后台主框架
- warehouse, location, SKU, supplier, and user panels / 仓库、库位、SKU、供应商、用户管理页面
- inbound, putaway, outbound, stocktake, inventory alert, and replenishment pages / 入库、上架、出库、盘点、库存预警、补货建议页面
- Playwright E2E coverage for smoke, permission, regression, and core workflows / Playwright 覆盖冒烟、权限、回归和核心流程用例

## E2E Tests / 端到端测试

```powershell
npm run e2e:install
npm run e2e:test:smoke
npm run e2e:test:core
```

This frontend is a portfolio demo, not a polished commercial product.

该前端是作品集演示项目，不是成熟商业产品。

