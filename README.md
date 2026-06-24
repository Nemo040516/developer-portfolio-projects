# Full-Stack Developer Portfolio / 全栈开发作品集

Selected projects for resume and interview review. The code has been reorganized for public access, with private academic files, local configuration, runtime uploads, logs, and generated output excluded.

这是一个用于简历和面试展示的代码作品集。仓库中的项目已按功能重新整理命名，公开内容聚焦于代码实现、业务建模、接口设计、数据库脚本、测试用例和项目文档，不包含论文、答辩材料、本机配置、上传文件、日志或构建产物。

## Portfolio Snapshot / 作品集概览

- **Resume / 简历**：[PDF resume](resume/lin-zijian-resume-2026.pdf) / [中文简历 PDF](resume/lin-zijian-resume-2026.pdf).
- **Main showcase / 主展示项目**：SME Recruitment and Application Management Platform / 中小商家招聘与投递管理平台，我的自有本科毕业设计项目。
- **Technical coverage / 技术覆盖**：Vue 3, Vite, Element Plus, Pinia, Spring Boot, Spring Security, JWT, MyBatis-Plus, MySQL, WebSocket, Flask, Playwright, JUnit, pytest.
- **Review focus / 审阅重点**：multi-role workflows, API design, database schema, frontend state and routing, access control, file upload permissions, realtime messaging, automated tests.
- **Public-safe project code / 公开安全项目代码**：project names are functional and anonymized; private school-only materials, credentials, session state, uploads, logs, and build output are excluded. The linked resume PDF is intentionally public contact material.

## Project Index / 项目索引

| Project | What It Demonstrates | Stack | Suggested Review Focus |
| --- | --- | --- | --- |
| [SME Recruitment and Application Management Platform](projects/sme-recruitment-platform)<br>中小商家招聘与投递管理平台 | Main showcase project. A multi-role recruitment platform for merchants, applicants, and administrators.<br>主展示项目。面向商家、求职者和管理员三类角色的招聘与投递管理平台。 | Vue 3, Vite, Element Plus, Pinia, Spring Boot, Spring Security, JWT, MyBatis-Plus, MySQL, WebSocket, Playwright | Role-based access, recruitment lifecycle, application management, WebSocket chat, governance notices, upload access control, backend and E2E tests.<br>角色权限、招聘业务流程、投递管理、即时沟通、治理通知、上传访问控制、后端与端到端测试。 |
| [Warehouse Management System](projects/warehouse-management-system)<br>仓储管理系统 | Anonymized collaboration / commissioned case covering common warehouse workflows.<br>匿名化协作 / 委托开发案例，覆盖常见仓储业务流程。 | Vue 3, Vite, Spring Boot, Spring Security, JWT, MySQL, Playwright | CRUD workflows, role-based menus, inbound, putaway, outbound, inventory, stocktake, replenishment, SQL scripts, integration tests.<br>CRUD 流程、权限菜单、入库、上架、出库、库存、盘点、补货建议、SQL 脚本和集成测试。 |
| [Dance Motion Analysis System](projects/dance-motion-analysis-system)<br>舞蹈动作视频比对与纠错系统 | Anonymized collaboration / commissioned Flask application for video comparison and feedback workflows.<br>匿名化协作 / 委托开发 Flask 项目，用于展示视频比对与反馈流程。 | Python, Flask, Jinja, HTML/CSS/JavaScript, pytest | Flask routing, service-layer separation, video workflow pages, comparison logic, local JSON storage, API and service tests.<br>Flask 路由、服务层拆分、视频流程页面、动作比对逻辑、本地 JSON 记录、接口与服务测试。 |

## Main Showcase / 主展示项目

### SME Recruitment and Application Management Platform

`projects/sme-recruitment-platform` is the primary project in this repository and is the recommended starting point for code review.

`projects/sme-recruitment-platform` 是本仓库的核心展示项目，建议作为代码审阅的第一入口。

The project implements a full recruitment workflow for small and medium-sized merchants:

- **Administrator / 管理员**：job review, merchant review, user ban, password reset, report handling, governance notices.
- **Merchant / 商家**：company profile, job publishing, candidate management, talent pool, interview scheduling.
- **Applicant / 求职者**：profile, online resume, job search, application tracking, interview list.
- **Shared workflows / 公共流程**：JWT authentication, route protection, WebSocket chat, file upload access checks, governance notifications.

Suggested entry points:

| Area | Path |
| --- | --- |
| Project overview / 项目总览 | [projects/sme-recruitment-platform/README.md](projects/sme-recruitment-platform/README.md) |
| Backend application code / 后端应用代码 | [projects/sme-recruitment-platform/backend/src/main/java/com/example/backend](projects/sme-recruitment-platform/backend/src/main/java/com/example/backend) |
| Frontend role pages / 前端角色页面 | [projects/sme-recruitment-platform/frontend/src/views](projects/sme-recruitment-platform/frontend/src/views) |
| Frontend API modules / 前端接口模块 | [projects/sme-recruitment-platform/frontend/src/api](projects/sme-recruitment-platform/frontend/src/api) |
| Database bootstrap scripts / 数据库初始化脚本 | [projects/sme-recruitment-platform/scripts/bootstrap](projects/sme-recruitment-platform/scripts/bootstrap) |
| Backend tests / 后端测试 | [projects/sme-recruitment-platform/backend/src/test](projects/sme-recruitment-platform/backend/src/test) |
| Frontend E2E tests / 前端端到端测试 | [projects/sme-recruitment-platform/frontend/e2e](projects/sme-recruitment-platform/frontend/e2e) |

## Verification / 本地验证

The main showcase project was locally verified before being published in this portfolio version.

主展示项目在整理进入公开作品集前已完成本地基础验证。

```powershell
# Backend / 后端
cd projects\sme-recruitment-platform\backend
.\mvnw.cmd test

# Frontend / 前端
cd projects\sme-recruitment-platform\frontend
npm ci
npm run build
```

Latest local result / 最近一次本地结果：

- Backend tests: **159 passed, 0 failed**.
- Frontend production build: **passed**.
- `npm audit` reported dependency vulnerabilities; they are documented as follow-up work before any real deployment.

## Repository Layout / 仓库结构

```text
developer-portfolio-projects/
  README.md
  PROJECT_SCOPE.md
  projects/
    sme-recruitment-platform/
      backend/
      frontend/
      scripts/
      README.md
    warehouse-management-system/
      frontend/
      backend/
      sql/
      README.md
    dance-motion-analysis-system/
      app/
      tests/
      scripts/
      README.md
```

## Recommended Review Path / 推荐阅读方式

For a quick interview review, start with the main project README, then inspect backend services, frontend role pages, API modules, database scripts, and tests.

如果用于简历或面试审阅，建议按下面顺序阅读：

1. Read [the main project README](projects/sme-recruitment-platform/README.md) to understand scope, roles, and local setup.
2. Review backend `controller / service / mapper` code for role permissions, recruitment flow, governance notices, and upload access control.
3. Review frontend `views / api / stores` code for role pages, route guards, API encapsulation, and state handling.
4. Review database scripts and tests to understand schema design, demo data, MockMvc coverage, and Playwright E2E coverage.

1. 先看 [主项目 README](projects/sme-recruitment-platform/README.md)，了解项目范围、角色划分和本地运行方式。
2. 再看后端 `controller / service / mapper`，重点关注角色权限、投递流程、治理通知和上传访问控制。
3. 再看前端 `views / api / stores`，重点关注三类角色页面、路由守卫、接口封装和状态管理。
4. 最后看数据库脚本和测试，确认表结构、演示数据、MockMvc 覆盖范围和 Playwright E2E 覆盖范围。

## Scope and Disclosure / 项目边界说明

This is a portfolio repository, not a production deployment package.

这是求职作品集仓库，不是可直接商用部署的生产系统包。

- The main showcase project is my own undergraduate graduation project.
- The additional projects are anonymized collaboration / commissioned development cases.
- Public code keeps the implementation structure that is useful for technical review.
- Private academic materials, local credentials, uploaded runtime files, logs, generated build output, and session state are excluded from project folders. The resume PDF is intentionally included as public contact material.
- These projects demonstrate practical implementation ability, but should not be presented as senior enterprise production delivery experience.

- 主展示项目是我的自有本科毕业设计项目。
- 补充项目是已匿名化处理的协作 / 委托开发案例。
- 公开仓库保留适合技术审阅的代码结构、测试和说明。
- 项目目录不包含论文、答辩材料、学校材料、本机凭据、上传文件、日志、构建产物和登录态；简历 PDF 作为主动公开的联系材料单独放在 `resume/` 目录。
- 这些项目用于展示实际开发能力，不应包装成资深企业级生产交付经验。

More details / 详细边界说明：[PROJECT_SCOPE.md](PROJECT_SCOPE.md)
