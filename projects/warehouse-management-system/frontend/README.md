# Warehouse Management System Frontend

Vue 3 + Vite frontend for the warehouse management system.

## Requirements

- Node.js 18+
- npm 9+

## Run

```powershell
npm install
npm run dev
```

Default local URL:

```text
http://localhost:5173
```

The Vite proxy forwards `/api` requests to `http://localhost:8080`.

## Implemented Areas

- login page and session token handling
- role-based menu visibility
- dashboard shell
- warehouse, location, SKU, supplier, and user panels
- inbound, putaway, outbound, stocktake, inventory alert, and replenishment pages
- Playwright E2E coverage for smoke, permission, regression, and core M1-M6 workflows

## E2E Tests

```powershell
npm run e2e:install
npm run e2e:test:smoke
npm run e2e:test:core
```

This frontend is part of a graduation-project-level portfolio case, not a polished commercial product.

