# Backend / 后端

## 中文说明

本目录是“中小商家招聘与投递管理平台”的 Spring Boot 后端，负责认证鉴权、角色权限、岗位管理、投递流程、面试安排、聊天、举报处理、平台治理通知和文件访问控制。

配置默认面向本地开发，真实密钥和数据库密码通过环境变量注入，不应写入仓库。

## English

This directory contains the Spring Boot backend for the SME recruitment platform. It handles authentication, role-based access, job management, application workflows, interview scheduling, chat, reports, governance notices, and upload access control.

The default configuration is for local development. Real secrets and database passwords should be injected through environment variables and must not be committed.

## Stack / 技术栈

- Java 17
- Spring Boot 3
- Spring Security
- JWT
- MyBatis-Plus
- MySQL
- WebSocket
- JUnit / Spring Boot Test / MockMvc

## Run / 运行

```powershell
.\mvnw.cmd spring-boot:run
```

Or from the project root / 也可以在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start_backend_local.ps1
```

## Configuration / 配置

- Main config / 主配置：`src/main/resources/application.yml`
- Mapper XML：`src/main/resources/mapper/`
- Required for real startup / 真实启动建议设置：`APP_JWT_SECRET`
- Optional database overrides / 可选数据库覆盖：`MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DB`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`

## Tests / 测试

```powershell
.\mvnw.cmd test
```

Some MySQL-specific integration tests require an initialized local MySQL database.

部分 MySQL 专项集成测试需要先初始化本地 MySQL 数据库。
