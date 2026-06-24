# Warehouse Management System Backend / 仓储管理系统后端

## 中文说明

这是仓储管理系统的 Spring Boot 后端部分，提供登录鉴权、基础资料、入库、上架、出库、库存、盘点、预警和补货建议等接口。

## English

This is the Spring Boot backend for the warehouse management system. It provides APIs for authentication, master data, inbound, putaway, outbound, inventory, stocktake, alerts, and replenishment suggestions.

## Requirements / 环境要求

- JDK 17
- Maven or Maven Wrapper
- MySQL 8.x

## Run / 运行

```powershell
.\mvnw.cmd spring-boot:run
```

Default service port / 默认服务端口：

```text
8080
```

## Environment Variables / 环境变量

| Variable | Default | Description / 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Backend HTTP port / 后端端口 |
| `DB_HOST` | `127.0.0.1` | MySQL host / MySQL 主机 |
| `DB_PORT` | `3306` | MySQL port / MySQL 端口 |
| `DB_NAME` | `wms_db` | Database name / 数据库名 |
| `DB_USERNAME` | `root` | Local database user / 本地数据库用户 |
| `DB_PASSWORD` | empty | Local database password / 本地数据库密码 |
| `JWT_SECRET` | development-only fallback | JWT signing secret / JWT 签名密钥 |
| `FRONTEND_DIST_DIR` | `./web/` | Optional static frontend build path / 可选前端静态资源路径 |

## Demo Accounts / 演示账号

The SQL seed data contains local demo accounts for academic testing. Change them before any real deployment.

SQL 初始化数据包含本地演示账号，仅用于课程设计 / 毕设演示。真实部署前必须修改。

## Main Modules / 主要模块

- authentication and role menu loading / 鉴权与角色菜单
- user, role, warehouse, location, SKU, and supplier management / 用户、角色、仓库、库位、SKU、供应商管理
- inbound and putaway workflows / 入库与上架流程
- outbound workflow / 出库流程
- stock and transaction queries / 库存与流水查询
- inventory alert rules / 库存预警规则
- stocktake workflow / 盘点流程
- replenishment suggestion workflow / 补货建议流程

## Tests / 测试

Integration tests are under / 集成测试位于：

```text
src/test/java/com/wms/backend
```

Run / 运行：

```powershell
.\mvnw.cmd test
```

This backend is intended as a resume code sample and interview discussion project.

该后端用于简历作品集和面试讨论，不是生产级后端系统。

