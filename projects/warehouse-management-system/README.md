# Warehouse Management System / 仓储管理系统

## 中文说明

这是一个**毕设级 / 课程设计级全栈项目**，覆盖仓储管理中的基础资料、入库、上架、出库、库存、盘点、预警和补货建议等流程。

该项目是匿名化整理后的**委托开发 / 协作开发项目案例**，用于展示全栈实现能力、数据库脚本设计、接口联调和测试意识。它不应被描述为企业级 WMS 产品。

已排除学校材料、个人标识、构建产物、本地日志和只用于交付的辅助文件。

## English

This is a **graduation-project-level / course-project-level full-stack project** covering common warehouse workflows such as master data, inbound, putaway, outbound, inventory, stocktake, alerts, and replenishment suggestions.

It is an anonymized **commissioned / collaborative development case** prepared for portfolio review. It demonstrates full-stack implementation, database scripting, API integration, and testing awareness, but it should not be described as an enterprise-grade WMS product.

School-specific materials, personal identifiers, generated build outputs, local logs, and delivery-only helper files are excluded.

## Tech Stack / 技术栈

- Frontend / 前端：Vue 3, Vite, Element Plus, Playwright
- Backend / 后端：Java 17, Spring Boot, Spring Security, JWT
- Database / 数据库：MySQL
- Tests / 测试：Spring Boot integration tests, Playwright E2E tests

## Main Features / 主要功能

- login and role-based menu loading / 登录与按角色加载菜单
- user, role, warehouse, location, SKU, and supplier management / 用户、角色、仓库、库位、SKU、供应商管理
- inbound order workflow / 入库流程
- putaway workflow / 上架流程
- outbound workflow / 出库流程
- inventory stock and transaction views / 库存和流水查询
- inventory alert rules / 库存预警规则
- stocktake workflow / 盘点流程
- replenishment suggestion workflow / 补货建议流程

## Layout / 目录结构

```text
warehouse-management-system/
  frontend/    Vue 3 frontend and Playwright tests
  backend/     Spring Boot backend and integration tests
  sql/         staged database scripts and sample data
```

## Run Locally / 本地运行

Start backend / 启动后端：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Start frontend / 启动前端：

```powershell
cd frontend
npm install
npm run dev
```

## Tests / 测试

Backend / 后端：

```powershell
cd backend
.\mvnw.cmd test
```

Frontend E2E / 前端端到端测试：

```powershell
cd frontend
npm install
npm run e2e:test:smoke
```

## Limitations / 项目限制

- The system uses demo seed data and local-development defaults. / 系统使用演示数据和本地开发默认配置。
- Permission and workflow rules are sufficient for an academic demo, not a production WMS. / 权限和流程规则适合毕设演示，不是生产级 WMS。
- No real deployment, monitoring, backup, or high-availability design is included. / 未包含真实部署、监控、备份和高可用设计。
- Password and JWT handling should be hardened before real use. / 密码和 JWT 处理在真实使用前需要进一步加固。

