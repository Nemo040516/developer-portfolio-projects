# Warehouse Management System Backend

Spring Boot backend for a graduation-project-level warehouse management system.

## Requirements

- JDK 17
- Maven or Maven Wrapper
- MySQL 8.x

## Run

```powershell
.\mvnw.cmd spring-boot:run
```

Default service port:

```text
8080
```

## Environment Variables

The project uses environment variables for local configuration:

| Variable | Default | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Backend HTTP port |
| `DB_HOST` | `127.0.0.1` | MySQL host |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `wms_db` | Database name |
| `DB_USERNAME` | `root` | Local database user |
| `DB_PASSWORD` | empty | Local database password |
| `JWT_SECRET` | development-only fallback | JWT signing secret |
| `FRONTEND_DIST_DIR` | `./web/` | Optional static frontend build path |

## Demo Accounts

The SQL seed data contains local demo accounts for academic testing. Change them before any real deployment.

## Main Modules

- authentication and role menu loading
- user, role, warehouse, location, SKU, and supplier management
- inbound and putaway workflows
- outbound workflow
- stock and transaction queries
- inventory alert rules
- stocktake workflow
- replenishment suggestion workflow

## Tests

Integration tests are under:

```text
src/test/java/com/wms/backend
```

Run:

```powershell
.\mvnw.cmd test
```

This backend is intended as a resume code sample and interview discussion project.

