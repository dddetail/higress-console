# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Higress Console is a management UI for the [Higress](https://github.com/alibaba/higress) API gateway. It configures gateway routes, domains, plugins, TLS certificates, AI/LLM providers, and MCP servers — all stored as Kubernetes CRDs. The backend is Java 8 / Spring Boot 2.7.18; the frontend is React 18 / ICE.js 3.x / Ant Design 4.

## Build & Run Commands

### Backend

```bash
cd backend
# Build (skips tests)
./mvnw clean package -Dmaven.test.skip=true
# Build with tests
./mvnw clean package
# Run a single test class
./mvnw -pl console test -Dtest=PermissionServiceTest
# Run locally (requires JDK 17+, connects to K8s cluster)
sh start.sh --local
```

The `console` module's `frontend-maven-plugin` builds the frontend automatically during `mvn package`. The built frontend is embedded into the Spring Boot JAR at `static/`.

### Frontend

```bash
cd frontend
npm install        # requires Node 16+
npm start          # dev server at localhost:3333, proxies /api -> localhost:8080
npm run build      # production build to build/
npm run lint       # ESLint + Stylelint
npm run check-i18n # validates i18n key consistency across locales
```

### Docker

```bash
# Full build + Docker image + export tar
bash build-and-deploy.sh
# Backend only
cd backend && sh build.sh
```

## Architecture

### Two-Module Maven Project

```
backend/
├── pom.xml          # Parent POM: Spring Boot 2.7.18, Java 8
├── sdk/             # Pure Java library (no Spring dependency)
│   └── src/main/java/com/alibaba/higress/sdk/
│       ├── service/     # All business logic: RouteService, DomainService, WasmPluginService, etc.
│       │   ├── ai/          # LLM provider handlers (OpenAI, Azure, Claude, Qwen, Bedrock, Ollama, vLLM, Vertex, ZhipuAI)
│       │   ├── consumer/    # Consumer credential handling
│       │   ├── kubernetes/  # K8s client + CRD model converter
│       │   └── mcp/         # MCP server with strategy pattern (Database, DirectRouting, OpenApi)
│       ├── model/       # Domain models
│       └── constant/    # Constants and enums
└── console/         # Spring Boot web application
    └── src/main/java/com/alibaba/higress/console/
        ├── controller/  # REST controllers (thin — delegate to SDK services)
        ├── aop/         # @RequirePermission + @AllowAnonymous AOP aspects for RBAC
        ├── service/     # Console-specific services: Session, User, OAuth2, Permission
        ├── repository/  # JPA repositories (MySQL)
        └── config/      # SdkConfig wires SDK services as Spring beans
```

**Key pattern**: Controllers are thin routing + permission layers. All gateway business logic lives in the SDK module, which talks directly to Kubernetes via `kubernetes-client-java`. The SDK services are manually constructed by `HigressServiceProviderImpl` and registered as Spring beans via `SdkConfig`.

### Frontend Structure

```
frontend/src/
├── app.ts              # ICE.js app config: router.basename=/console, auth, store, dataLoader
├── i18n.ts             # i18next init (zh-CN default, en-US)
├── store.ts            # ICE store: user, config, system models
├── constants.ts        # APP_BASE_PATH = '/console'
├── pages/              # File-system routed pages
│   ├── layout.tsx      # ProLayout shell with role-based menu, i18n, anti-iframe
│   ├── _defaultProps.tsx  # Route tree + menu config (requiredRole, visiblePredicate)
│   ├── ai/             # AI provider/route management
│   ├── mcp/            # MCP server management
│   └── ...             # dashboard, route, domain, plugin, consumer, user, etc.
├── services/           # Axios API modules (one per domain, import from request.tsx)
├── interfaces/         # TypeScript types mirroring backend models
├── components/         # Shared components: PermissionGuard, CodeEditor, ServiceWeightTable, etc.
├── models/             # ICE store models (Redux-based)
├── locales/            # zh-CN/ and en-US/ translation.json
└── utils/              # Shared utilities
```

### API Patterns

- Backend controllers: `@RestController` + `@RequestMapping("/v1/...")` + `@Tag` (Swagger) + `@RequirePermission(resource, action)`
- All responses wrapped via `ControllerUtil.buildResponseEntity()` into `Response<T>` / `PaginatedResponse<T>`
- Frontend services: Axios instance in `services/request.tsx` with JWT token injection, 401 redirect, i18n error modals
- API versioning: all backend endpoints under `/v1/` prefix

### Authentication & RBAC

Two AOP aspects intercept all controller calls in order:
1. **ApiStandardizationAspect** — session validation (cookie/Basic auth/OAuth2), MDC trace ID, global exception handling
2. **RbacAspect** — checks `@RequirePermission` against role-resource-action matrix

Roles (descending privilege): `platform_admin` → `owner` → `manager` → `reader`

### Database

MySQL via JPA + Flyway migrations (`backend/console/src/main/resources/db/migration/`):
- Tables: `user`, `oauth2_provider`, `oauth2_account`, `consumer_info`, `consumer_member`
- Configured via env vars: `HIGRESS_DB_URL`, `HIGRESS_DB_USERNAME`, `HIGRESS_DB_PASSWORD`
- `ddl-auto=none` — schema changes must go through Flyway migrations

### Plugin System

The SDK bundles 40+ built-in Wasm plugin specs as YAML files under `sdk/src/main/resources/plugins/`. Velocity templates under `sdk/src/main/resources/templates/` generate EnvoyFilter configurations.

## i18n

- Backend: Not internationalized (English-only API responses)
- Frontend: i18next with `useTranslation()` hook, keys like `menu.dashboard`, `aiRoute.columns.name`
- Translation files: `src/locales/{zh-CN,en-US}/translation.json`
- **When adding user-facing strings**, always add keys to both locale files
- Run `npm run check-i18n` to validate consistency

## Key Configuration Files

| File | Purpose |
|------|---------|
| `backend/pom.xml` | Parent Maven POM |
| `backend/console/src/main/resources/application.properties` | Spring Boot config (datasource, Flyway, Swagger, OAuth2) |
| `frontend/package.json` | NPM scripts and dependencies |
| `frontend/ice.config.mts` | ICE.js build config, dev proxy `/api` → `localhost:8080` |
| `frontend/tsconfig.json` | TypeScript config, path alias `@/*` → `./src/*` |
| `helm/values.yaml` | Helm chart values (image, service, admin credentials, observability) |

## Development Workflow

1. Frontend dev: `npm start` in `frontend/` — hot-reload on port 3333, API calls proxied to backend on 8080
2. Backend dev: `sh start.sh --local` in `backend/` — requires access to a Kubernetes cluster with Higress CRDs
3. Full build: `cd backend && ./mvnw clean package` — builds frontend automatically, produces JAR with embedded frontend
4. All gateway config (routes, domains, plugins) is stored as Kubernetes CRDs — the backend has no local persistence for these

## CI

- `.github/workflows/build-and-test.yaml` — Java 21, `mvn clean package`
- `.github/workflows/frontend-code-checker.yaml` — Frontend linting
- `.github/workflows/codeql-analysis.yaml` — Security scanning
