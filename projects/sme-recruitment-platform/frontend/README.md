# Frontend / 前端

## 中文说明

本目录是“中小商家招聘与投递管理平台”的 Vue 3 前端，包含管理员、商家、求职者三类角色页面。页面覆盖登录注册、职位大厅、岗位详情、在线简历、投递记录、商家岗位管理、候选人管理、面试安排、聊天、举报和平台治理通知等流程。

## English

This directory contains the Vue 3 frontend for the SME recruitment platform. It includes administrator, merchant, and applicant views for authentication, job browsing, job detail, resume management, applications, merchant job management, candidate management, interview scheduling, chat, reports, and governance notices.

## Stack / 技术栈

- Vue 3
- Vite
- Element Plus
- Pinia
- Axios
- Playwright

## Setup / 启动

```powershell
npm ci
npm run dev
```

The public `.env.example` points the frontend to `http://localhost:8080`.

公开的 `.env.example` 默认指向 `http://localhost:8080`。

## Build / 构建

```powershell
npm run build
npm run preview
```

## E2E / 端到端测试

```powershell
npm run e2e:smoke
```

`playwright.config.js` starts the local Vite server for browser tests. Business-flow E2E tests require the backend and database to be available.

`playwright.config.js` 会为浏览器测试启动本地 Vite 服务。业务闭环 E2E 需要后端和数据库可用。

## Layout / 目录约定

- `src/api/`: API request wrappers / 接口请求封装
- `src/router/`: route definitions and guards / 路由与守卫
- `src/stores/`: Pinia stores / Pinia 状态
- `src/views/`: role-based pages / 分角色页面
- `src/components/`: reusable components / 可复用组件
- `e2e/`: Playwright tests and support code / Playwright 测试与辅助代码
