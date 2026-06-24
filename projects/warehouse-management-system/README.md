# Warehouse Management System

A graduation-project-level full-stack warehouse management system covering common inventory workflows.

## Portfolio Context

This is an anonymized **classmate-commissioned / collaborative graduation-project development case** reorganized for resume review. It shows full-stack implementation ability, but it should not be described as an enterprise-grade WMS product.

Original school materials, personal identifiers, generated build outputs, local logs, and delivery-only helper files are excluded.

## Tech Stack

- Frontend: Vue 3, Vite, Element Plus, Playwright
- Backend: Java 17, Spring Boot, Spring Security, JWT
- Database: MySQL
- Tests: Spring Boot integration tests, Playwright E2E tests

## Main Features

- login and role-based menu loading
- user, role, warehouse, location, SKU, and supplier management
- inbound order workflow
- putaway workflow
- outbound workflow
- inventory stock and transaction views
- inventory alert rules
- stocktake workflow
- replenishment suggestion workflow

## Layout

```text
warehouse-management-system/
  frontend/    Vue 3 frontend and Playwright tests
  backend/     Spring Boot backend and integration tests
  sql/         staged database scripts and sample data
```

## Run Locally

Start backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Start frontend:

```powershell
cd frontend
npm install
npm run dev
```

## Tests

Backend:

```powershell
cd backend
.\mvnw.cmd test
```

Frontend E2E:

```powershell
cd frontend
npm install
npm run e2e:test:smoke
```

## Limitations

- The system uses demo seed data and local-development defaults.
- Permission and workflow rules are sufficient for an academic demo, not a production WMS.
- No real deployment, monitoring, backup, or high-availability design is included.
- Password and JWT handling should be hardened before real use.

