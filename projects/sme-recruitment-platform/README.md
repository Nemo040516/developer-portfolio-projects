# SME Recruitment and Application Management Platform / 中小商家招聘与投递管理平台

## 中文说明

这是我的**自有本科毕业设计项目**，整理后作为本作品集的主展示项目。项目面向中小商家招聘场景，覆盖管理员、商家和求职者三类角色，从岗位发布、职位审核、简历维护、岗位投递、候选人管理、面试安排，到即时沟通、举报处理和平台治理通知等流程。

这个项目适合作为面试中的全栈代码样例：可以讨论需求拆分、角色权限、接口设计、数据库表结构、前后端联调、文件上传控制、WebSocket 消息、测试覆盖和本地启动方式。它仍然是毕业设计级项目，不应包装成企业生产级 SaaS。

公开仓库只保留代码、测试、数据库结构和安全处理过的演示数据；论文、答辩材料、学校材料、上传文件、日志、本机配置和私有运行记录均未提交。

## English

This is my **own undergraduate graduation project**, reorganized as the main showcase project in this portfolio. It targets recruitment workflows for small and medium-sized merchants and includes three roles: administrator, merchant, and applicant. The system covers job publishing, job review, resume/profile maintenance, job applications, candidate management, interview scheduling, chat, reports, and platform governance notices.

The project is useful for interview discussion around requirements breakdown, role-based access, API design, database schema, frontend/backend integration, upload access control, WebSocket messaging, test coverage, and local setup. It is still a graduation-project-level system, not an enterprise production SaaS.

Only public code, tests, schema scripts, and sanitized demo data are included. Thesis papers, defense materials, school-specific files, uploaded runtime files, logs, local configuration, and private records are excluded.

## Tech Stack / 技术栈

- Frontend / 前端：Vue 3, Vite, Element Plus, Pinia, Axios, Playwright
- Backend / 后端：Java 17, Spring Boot, Spring Security, JWT, MyBatis-Plus, WebSocket
- Database / 数据库：MySQL, H2 for selected tests
- Tests / 测试：JUnit, Spring Boot tests, MockMvc, Playwright E2E
- Scripts / 脚本：PowerShell local bootstrap and start scripts

## Main Features / 主要功能

- multi-role login and route protection / 多角色登录与路由保护
- administrator job review, merchant review, user ban, password reset, report handling / 管理员岗位审核、商家审核、用户封禁、密码重置、举报处理
- merchant company profile, job lifecycle, candidate list, talent pool, interview scheduling / 商家资料、岗位生命周期、候选人列表、人才库、面试安排
- applicant profile, resume maintenance, job search, application tracking, interview list / 求职者资料、在线简历、职位检索、投递跟踪、面试列表
- WebSocket chat and unread state handling / WebSocket 即时沟通与未读状态处理
- governance notices and rectification workflows / 平台治理通知与整改流程
- upload access checks for resumes, qualification files, and report evidence / 简历、资质、举报证据等上传访问控制

## Layout / 目录结构

```text
sme-recruitment-platform/
  backend/              Spring Boot backend and tests
  frontend/             Vue 3 frontend and Playwright tests
  scripts/
    bootstrap/          MySQL schema and sanitized demo seed
    bootstrap_local_dev.ps1
    start_backend_local.ps1
    start_frontend_local.ps1
  docs/                 Public project notes only
```

## Run Locally / 本地运行

Prerequisites / 前置条件：

- Java 17
- Node.js 20.19+ or 22.12+
- MySQL 8.x
- PowerShell on Windows

Initialize database / 初始化数据库：

```powershell
cd projects\sme-recruitment-platform
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap_local_dev.ps1 -MysqlUsername root -MysqlPassword "your-local-password"
```

Start backend / 启动后端：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start_backend_local.ps1
```

Start frontend / 启动前端：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start_frontend_local.ps1
```

Default demo accounts / 默认演示账号：

```text
admin1 / 12345
boss1  / 12345
app1   / 12345
```

These accounts and seed records are synthetic local demo data only.

这些账号和种子记录只用于本地演示，均为合成数据。

## Tests / 测试

Backend / 后端：

```powershell
cd backend
.\mvnw.cmd test
```

Frontend build / 前端构建：

```powershell
cd frontend
npm ci
npm run build
```

Frontend E2E / 前端端到端测试：

```powershell
cd frontend
npm run e2e:smoke
```

Some E2E and MySQL integration tests require the local backend and database to be running.

部分 E2E 与 MySQL 集成测试需要先启动本地后端和数据库。

## Public Repository Notes / 公开仓库说明

- `.env`, `.env.local`, logs, uploads, generated reports, `target/`, `dist/`, `node_modules/`, and Playwright session state are ignored.
- Uploaded resumes, report evidence, qualification files, generated screenshots, and school documents are not included.
- `scripts/bootstrap/seed_local_dev_minimal_20260402.sql` contains sanitized demo data only.
- The code keeps the original project architecture where possible, with public-facing documentation added around it.

- `.env`、`.env.local`、日志、上传目录、生成报告、`target/`、`dist/`、`node_modules/` 和 Playwright 登录态都被忽略。
- 简历附件、举报证据、资质文件、生成截图和学校材料不进入公开仓库。
- `scripts/bootstrap/seed_local_dev_minimal_20260402.sql` 只包含安全处理过的演示数据。
- 代码尽量保留原项目结构，只补充公开展示需要的说明。
