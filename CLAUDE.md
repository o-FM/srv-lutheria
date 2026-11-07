# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Multi-tenant backend for luthier workshops (customers/instruments, stock, service catalog, service orders with quotes and a customer portal, notifications, reports, audit, LGPD). It is a **modular monolith**: Java 21, Spring Boot 4.1, Spring Modulith 2.1. Each module is its own Maven artifact and is meant to be extractable into a microservice later. The docs and code comments are in Portuguese (pt-BR). The front-end lives in a separate repo (`srv-lutheria-front`).

## Commands

Use `./mvnw` (or `mvnw.cmd` on Windows). The integration tests live in `srv-luthier-app`, so they need the other modules built first (`-am`).

```bash
./mvnw install                         # build everything + all tests
./mvnw -B -ntp verify                  # what CI runs
./mvnw install -DskipTests             # build only

# Run a single test class / method (tests are in srv-luthier-app)
./mvnw -pl srv-luthier-app -am test -Dtest=ServiceOrderFlowTest -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -pl srv-luthier-app -am test -Dtest='ServiceOrderFlowTest#fullLifecycle' -Dsurefire.failIfNoSpecifiedTests=false

# Run locally (default profile "dev": in-memory H2 in PostgreSQL mode, port 8080, Swagger/api-docs + /h2-console on)
java -jar srv-luthier-app/target/srv-luthier-app-1.0.0-SNAPSHOT.jar
# Dev admin: admin@luthieria.local / dev-admin-12345

# Full stack (PostgreSQL, app on the prod profile, Mailpit, Prometheus, Jaeger)
./mvnw install -DskipTests && docker compose up --build
```

- `PostgresMigrationTest` uses Testcontainers and is **skipped silently when Docker is not running**. Start Docker to check migrations against real PostgreSQL.
- `ModularityTest` fails the build on any module-boundary violation and writes C4/PlantUML diagrams to `srv-luthier-app/target/spring-modulith-docs`.
- Load tests (k6): see `load-tests/README.md`.
- CI (`.github/workflows/ci.yml`) also runs a Trivy scan that fails on HIGH/CRITICAL vulnerabilities, secrets or misconfigs.

## Architecture

### Modules and boundaries

| Maven module | Package | May depend on |
|---|---|---|
| `srv-luthier-shared` | `com.luthier.shared` | — (open kernel: errors, tenancy, validation, authorization constants, `AuditEvent`, `PageResponse`) |
| `srv-luthier-security` | `com.luthier.security` | shared |
| `srv-luthier-customer` | `com.luthier.customer` | shared |
| `srv-luthier-product` | `com.luthier.product` | shared |
| `srv-luthier-service` | `com.luthier.service` | shared, customer, product (the only business module that uses other modules) |
| `srv-luthier-notification` | `com.luthier.notification` | shared + public APIs; it only listens to events |
| `srv-luthier-app` | `com.luthier` | everything: the `Application` main class, `application*.yaml`, all integration tests |
| `srv-luthier-parent` | — | the common dependency set every module inherits (web, JPA/Modulith, Flyway, actuator, OTel, test libs) |

- Each module's allowed dependencies are declared in its `package-info.java` (`@ApplicationModule(allowedDependencies = ...)`). To add a cross-module dependency, update that annotation **and** the module's `pom.xml`.
- **Public API = types in the module's root package** (e.g. `CustomerQueries`, `ProductQueries`, event records such as `StockConsumptionRequested`). Subpackages (`controllers`, `domain.*`, `services.impl`) are internal. Other modules must not reference them, or `ModularityTest` fails.
- Layering inside a module: `controllers` → `services` (interfaces) → `services/impl` → `domain/{entities,repositories,records,enums}`. DTOs are Java records in `domain/records`.
- The root `pom.xml` only manages versions (BOMs + internal modules). Nothing inherits dependencies from it.

### Cross-module events

Modules talk through Spring application events: `StockConsumptionRequested`, `ServiceOrderStatusChanged`, `CustomerAnonymized`, `StockLow`, `TenantRegistered`, `PasswordResetRequested`, `AuditEvent`. Async listeners use `@Async @TransactionalEventListener` and go through the Modulith JPA outbox (`event_publication` table; `completion-mode: delete`, republished on restart). Example: when an order reaches `pronto`, the service module publishes `StockConsumptionRequested`, and the product module deducts stock asynchronously.

### Multi-tenancy (shared database)

- Each workshop is a tenant (`tenant_tb`). Business entities extend `shared.tenancy.TenantScopedEntity`. Its `@TenantId` column makes Hibernate filter **every** query and set `tenant_id` on INSERT. Never set `tenant_id` by hand. The class also provides `created_at`/`updated_at` and `@Version` optimistic locking.
- The JWT carries `tid`. `TenantContextFilter` puts it into `TenantContext`, and `TenantIdentifierResolver` passes it to Hibernate.
- **Async listeners have no request or JWT.** Every event must carry `tenantId`, and the listener must wrap its work in `TenantContext.runAs(tenantId, ...)` **before** the transaction opens, because the Hibernate session pins the tenant when it opens. Hence the separate `@Transactional` inner `Consumer` bean in `StockConsumptionListener`.
- **Tenancy fails closed.** With no tenant in `TenantContext`, `TenantIdentifierResolver` returns the `NO_TENANT` sentinel, not `ROOT`. Business reads come back empty, bulk UPDATE/DELETE touch nothing, and persisting a `TenantScopedEntity` throws `IllegalStateException`. The unfiltered all-tenants view exists only inside `TenantContext.callAsPlatform(...)`. It is read-only and, like `runAs`, must wrap the transaction (see `NotificationRetentionJob` and its inner `Purger`). Every business table also has an FK `tenant_id → tenant_tb`, and `TenantIsolationTest` fails if a new table with `tenant_id` lacks one.
- `user_tb` and `audit_log_tb` have `tenant_id` but **no** automatic filter (login runs before the tenant is known), so their services must filter explicitly.
- Routes without a tenant: `/auth/**` and the customer portal (`/portal/orders/{token}`). The portal resolves the tenant from the order token and then runs inside that tenant.
- A record that belongs to another tenant must return **404**. Uniqueness constraints (CPF, SKU, title…) are per tenant.

### Database migrations

Flyway with `ddl-auto: validate`: the schema comes only from migrations, and Hibernate only checks that the entities match. Each module ships its migrations inside its own JAR under `src/main/resources/db/migration/<module>/`. All modules share **one** Flyway history, so version numbers are global across modules (current: `V1_0`…`V1_4`, `V2_0`, `V3_0`, `V4_0`…`V4_6`). Choose a version higher than every existing one in every module. Migrations must run on both H2 (`MODE=PostgreSQL`, used in dev and most tests) and PostgreSQL.

### Security and API conventions

- Stateless JWT, 15-minute access tokens plus opaque refresh tokens. Roles: ADMIN, LUTHIER, ATENDENTE, ESTOQUISTA. Use the SpEL constants in `shared.authorization.Access` (e.g. `@PreAuthorize(Access.CUSTOMER_DESK)`) instead of writing role expressions inline.
- `/auth/**` and the portal are rate-limited (`RateLimitFilter`). Integration tests raise the limits through `@SpringBootTest(properties = "app.security.rate-limit.*.capacity=1000")`.
- Errors use RFC 9457 `application/problem+json` via `shared.errors.GlobalExceptionHandler`. Throw `NotFoundException`, `ConflictException` or `BadRequestException`.
- DELETE is a soft delete. Enum JSON values follow the front-end in lowercase pt-BR (`violao_aco`, `em_execucao`, `pix`). CPF is always masked in responses (`***.982.247-**`), except in the LGPD export.
- Service-order state machine, the endpoint/role matrix and examples: `docs/api.md`. OpenAPI contract: `docs/openapi.json` (also at `GET /v3/api-docs` in dev). Security/LGPD decisions: `docs/security-review.md`.

### Configuration profiles

- `application.yaml` holds the secure base defaults (HTTPS required, Swagger off, no error details).
- `application-dev.yaml` is the default local profile and relaxes them (H2, permissive CORS, dev secrets).
- `application-prod.yaml` is used in production. `JWT_SECRET` (≥ 32 bytes) and `ADMIN_SENHA` (≥ 12 characters) are mandatory there, with no defaults. The full list of environment variables is in `README.md`.

### Tests

Integration tests are end-to-end flows in `srv-luthier-app/src/test/java/com/luthier/` (`*FlowTest`, `PrivacyAndSecurityTest`). They run on MockMvc against H2. `support/ApiClient` registers a fresh workshop per test (`registerWorkshop`), which keeps tests isolated by tenant, and provides `create(...)`/`auth(...)` helpers. Async effects are asserted with Awaitility.
