# Plan: Fase 7, BAIXOS

## Summary
Fecha os 23 achados BAIXOS de `docs/correcoes.prd.md` mais SH-07 e SEC-21 ("Achados surgidos"): 21 são corrigidos e 2 ficam aceitos com justificativa (SEC-17 e a parte de `equals`/`hashCode` do SH-04). As decisões de negócio de 2026-11-07 estão aplicadas (SEC-17, PRO-05, SRV-12, SRV-14). O trabalho fica em **6 branches**, a partir de `develop` **depois do merge da fase 6**, com as migrations `V7_0` a `V7_4` reservadas.

## User Story
Como dono de uma luthieria e como operador da plataforma,
quero que os detalhes de segurança, LGPD, dados e infraestrutura que sobraram da auditoria estejam resolvidos ou aceitos de forma explícita,
para que a auditoria fique fechada e nenhum risco pequeno fique esquecido.

## Problem → Solution
23 + 2 achados BAIXOS abertos (mensagens em inglês, PK UUIDv4, token do portal eterno, clientes inativos guardados para sempre, compose aberto em `0.0.0.0`, CI sem SHA…) → cada um corrigido com teste ou aceito com justificativa em `docs/auditoria.md`, e documentação alinhada.

## Metadata
- **Complexity**: Large (≈ 55 arquivos, a maioria de mudança pequena; o SH-04 toca 17 entidades)
- **Source PRD**: `docs/correcoes.prd.md`
- **PRD Phase**: Fase 7, BAIXOS
- **Estimated Files**: ≈ 55 (≈ 35 de produção, 10 de teste, 5 migrations, infra e docs)

---

## Decisões aplicadas (registradas no PRD em 2026-11-07)

| ID | Decisão | Efeito |
|---|---|---|
| SEC-17 | O mesmo CPF/CNPJ **pode** abrir várias luthierias (filiais) | Achado **aceito**, sem constraint, com justificativa em `docs/auditoria.md` |
| PRO-05 | **Reativar** o produto | O 409 de SKU/código de barras indica o produto desativado e o id dele. Endpoint novo `POST /products/{id}/activate` (STOCK_WRITE) |
| SRV-12 | O token do portal vale até **N dias depois de entregue/cancelada** (padrão 30, configurável) | Coluna `closed_at` na OS; o portal dá 404 depois do prazo. O token **sai do evento** `ServiceOrderStatusChanged`, e a notificação o busca por uma API pública nova |
| SRV-14 | **Anonimização automática** do cliente inativo há mais de 5 anos (1825 dias, configurável), e a exportação inclui as notificações | Colunas `deactivated_at`/`anonymized_at`; job diário; SPI para a exportação |
| SEC-13, NOT-06 | A auditoria os aceita, mas a correção é barata | **Corrigidos** (senha conferida antes do "desativado"; métrica depois do commit) |
| SEC-18 | Desligar o console H2 | `spring.h2.console.enabled` sai do dev, a dependência sai do parent e o `CLAUDE.md` é corrigido |

---

## Pré-condição (GATE 0, obrigatório)

`develop` precisa conter a **fase 6 inteira** (plano `.claude/PRPs/plans/fase-6-medios.plan.md`, branches 1 a 7). Confira:
```bash
git log --oneline origin/develop | grep -E "app-02|sec-auth-account|sec-03|service-order-rules|notification-delivery|customer-privacy|sh-06"
ls srv-luthier-notification/src/main/resources/db/migration/notification/V6_3__*.sql
```
Se faltar algo, **pare**. A fase 7 mexe nos mesmos arquivos e depende de:
- `AuditEvent.actor` (SEC-07), usado no job de retenção;
- `ServiceOrderStatusChanged.eventId` (NOT-02), que convive com a remoção do `trackingToken`;
- `Notifier.recordResult` (NOT-02), onde entra a métrica do NOT-06;
- `CustomerDeactivationGuard` (CUS-03) e o scrub completo da anonimização (CUS-02), usados pelo job do SRV-14;
- `PlatformScopeArchitectureTest` (SH-06), cuja allowlist recebe o job novo;
- os beans `Writer` do SEC-19/SEC-20, que já trocam parte do `jakarta.transaction` (SEC-15).

Se algum nome acima mudou na implementação da fase 6, use o nome real e anote no relatório.

---

## UX Design

Internal change. As mudanças de contrato estão abaixo; o front (`srv-lutheria-front`) precisa ser avisado.

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Todas as respostas de erro | Mistura "User not found!", "Invalid Data", "Parametros inválidos" | Tudo em pt-BR ("Usuário não encontrado", "Dados inválidos", "Parâmetros inválidos") | SH-03. Nenhum teste checa as mensagens antigas (conferido) |
| IDs novos | UUIDv4 | UUIDv7 (ordenável). Mesmo formato na API | SH-04 |
| `POST /products` / `PUT` com SKU ou código de produto desativado | 409 "SKU already registered" | 409 "O SKU pertence a um produto desativado (id …). Reative-o em POST /products/{id}/activate" | PRO-05 |
| `POST /products/{id}/activate` | — | **Novo**: 200 com o produto | PRO-05, STOCK_WRITE |
| `POST /products/{id}/movements` com `orderId` | Aceito sem validar | **400** ("orderId é preenchido só pela baixa automática da OS") | PRO-06 |
| `GET /portal/orders/{token}` de OS entregue/cancelada há mais de 30 dias | 200 para sempre | **404** | SRV-12 |
| `GET /privacy/customers/{id}/export` | Sem notificações | Campo `notifications` | SRV-14 |
| Qualquer POST/PUT com corpo acima de 64 KB | Aceito (até ~20 MB) | **413** `application/problem+json` | APP-05 |
| `/h2-console` (dev) | Ligado, mas inacessível | Desligado | SEC-18 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `CLAUDE.md` | all | Fronteiras, tenancy, migrations globais |
| P0 | `docs/auditoria.md` | seções dos 25 achados | Texto oficial |
| P0 | `.claude/PRPs/plans/fase-6-medios.plan.md` | Tasks 2.4, 4.3, 5.3, 6.4, 7.1 | Peças da fase 6 que esta fase estende |
| P0 | `srv-luthier-shared/.../errors/GlobalExceptionHandler.java` | 36-68 | SH-03 |
| P0 | `srv-luthier-shared/.../tenancy/TenantContext.java` | 66-117 | SH-07 |
| P0 | `srv-luthier-service/.../services/impl/CustomerPortalServiceImpl.java` | all | SRV-12 |
| P0 | `srv-luthier-customer/.../services/impl/CustomerServiceImpl.java` | `deactivate`, `anonymize` | SRV-14 |
| P1 | `srv-luthier-security/.../configs/SecurityConfig.java` | 30-95 | SEC-13, APP-05 |
| P1 | `srv-luthier-security/.../services/impl/AuthServiceImpl.java` | 58-83 | SEC-14, SEC-21 |
| P1 | `srv-luthier-security/.../configs/SegurancaProperties.java` | all | APP-05 (propriedade nova) |
| P1 | `srv-luthier-notification/.../services/impl/NotificationRetentionJob.java` | all | Padrão de job de plataforma (SRV-14) |
| P1 | `srv-luthier-product/.../services/impl/ProductServiceImpl.java` | 39-100 | PRO-05, PRO-06 |
| P1 | `srv-luthier-service/.../services/impl/ServiceCatalogServiceImpl.java` | all | SRV-13 |
| P1 | migrations `V1_0`, `V1_2`, `V1_4`, `V2_0`, `V5_1`, `V5_2` | all | Nomes de constraint e índice |
| P2 | `docker-compose.yml`, `.github/workflows/ci.yml`, `Dockerfile`, `.github/dependabot.yml` | all | APP-06/07/08 |
| P2 | `srv-luthier-app/src/test/java/com/luthier/RegistrationFlowTest.java` | 156-190 | APP-09 |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| UUIDv7 no Hibernate | `hibernate-core` 7.4.5 local: `org.hibernate.annotations.UuidGenerator.Style` tem `RANDOM, TIME, VERSION_6, VERSION_7, AUTO` | Use `@Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7)` **no lugar de** `@GeneratedValue(strategy = GenerationType.UUID)` (não use os dois juntos) |
| `DaoAuthenticationProvider` | Spring Security 7 (`AbstractUserDetailsAuthenticationProvider.setPreAuthenticationChecks`/`setPostAuthenticationChecks`) | O padrão confere `disabled` **antes** da senha. Trocando o pré-check por um no-op e o pós-check pelos checks padrão, a senha é conferida primeiro |
| Jackson 3 | O projeto usa `tools.jackson` (Boot 4) | Configurar `StreamReadConstraints` muda de API no Jackson 3. Por isso o APP-05 usa **filtro de tamanho do corpo**, que não depende da versão do Jackson |
| Spans HTTP | Micrometer Observation (`http.url`, alta cardinalidade, no `DefaultServerRequestObservationConvention`) | O path completo com o token do portal vai para o Jaeger. Um `ObservationFilter` reescreve o valor (SRV-12) |
| Digest de imagem | `docker buildx imagetools inspect eclipse-temurin:21-jre-alpine` | Devolve o `sha256:` para fixar no `FROM` (APP-08) |
| SHA de action | `git ls-remote https://github.com/actions/checkout refs/tags/v4` (idem para as outras) | SHA de 40 caracteres + comentário com a tag (APP-07) |

---

## Patterns to Mirror

### ERROR_HANDLING / mensagens
```java
// SOURCE: srv-luthier-service/.../domain/entities/ServiceOrderEntity.java:340-344
throw new ConflictException("Itens só podem ser alterados antes do envio do orçamento");
// SOURCE: GlobalExceptionHandler.java:67 (já em pt-BR)
return ResponseEntity.badRequest().body(problem("Dados inválidos", errors));
```
Mensagens curtas em pt-BR, sem "!" no fim, com acento.

### JOB DE PLATAFORMA (modelo do SRV-14)
```java
// SOURCE: srv-luthier-notification/.../services/impl/NotificationRetentionJob.java:30-57
@Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
public void purge() {
  var cutoff = Instant.now().minus(retention);
  int removed = TenantContext.callAsPlatform(() -> purger.deleteOlderThan(cutoff));
  log.info("Retenção: {} notificações removidas", removed);
}
@Component
static class Purger { @Transactional public int deleteOlderThan(Instant cutoff) { ... } }
```
O `callAsPlatform` é **só leitura** (gravar `TenantScopedEntity` lança exceção). Para gravar por luthieria, leia os pares `(tenantId, id)` no escopo de plataforma e grave cada um dentro de `TenantContext.runAs(tenantId, ...)`.

### SPI ENTRE MÓDULOS (modelo do SRV-14/exportação e SRV-12/link)
```java
// SOURCE: fase 6, Task 4.3 — com.luthier.customer.CustomerDeactivationGuard
public interface CustomerDeactivationGuard { void assertCanDeactivate(UUID customerId); }
```
A interface fica no **pacote raiz** do módulo que a consome; quem depende dele a implementa. Para uma API pública de leitura (ex.: `CustomerQueries`), a interface fica no pacote raiz de quem **implementa**.

### MIGRATION_PATTERN
```sql
-- SOURCE: srv-luthier-product/.../V5_2__product_hardening.sql:1-19
-- Fase 5 (hardening), módulo product.
-- Antes do deploy, cada consulta precisa dar 0: ...
-- A troca de índice vem ANTES de recriar a FK: o H2 prende à FK o primeiro índice compatível
-- e depois não deixa apagá-lo.
create index idx_movement_tenant_product_created on stock_movement_tb (tenant_id, product_id, created_at desc);
drop index idx_movement_tenant_product;
```

### TEST_STRUCTURE
Fluxo: `@SpringBootTest(properties = "app.security.rate-limit.login.capacity=1000")` + `@AutoConfigureMockMvc` + `ApiClient` (`registerWorkshop`, `create`, `auth`). Assíncrono: `await().atMost(Duration.ofSeconds(10))`. Unitário de shared: JUnit + AssertJ, sem Spring (`CpfTest`, `TenantContextTest`).

---

## Branches e versões reservadas

| # | Branch | Achados | Migration |
|---|---|---|---|
| 1 | `fix/low-code-hygiene` | SH-03, SH-04, SH-05, SH-07, SRV-15, APP-09 | — |
| 2 | `fix/low-security` | SEC-13, SEC-14, SEC-15, SEC-16, SEC-17 (aceito), SEC-18, SEC-21, APP-05 | `V7_0` (security) |
| 3 | `fix/low-data-rules` | CUS-06, PRO-05, PRO-06, SRV-13 | `V7_1` (customer), `V7_2` (service) |
| 4 | `change/srv-12-portal-token-lifetime` | SRV-12 | `V7_3` (service) |
| 5 | `change/srv-14-lgpd-retention` | SRV-14, NOT-05 (verificação), NOT-06 | `V7_4` (customer) |
| 6 | `chore/infra-hardening` | APP-06, APP-07, APP-08 | — |

**Ordem de merge:** 2 → 3 → 4 → 5 (versões crescentes). As branches 1 e 6 entram em qualquer ordem, mas a **1 de preferência primeiro**: o SH-04 toca todas as entidades e o SH-03 várias mensagens, e quanto antes entrar, menos rebase. Conflitos esperados e triviais: 1 × 3 (`ProductServiceImpl`, `ServiceCatalogServiceImpl`: mensagens × regras); 1 × 4 (`CustomerPortalServiceImpl`: mensagem × expiração).

---

## Files to Change

| File | Action | Branch | Justification |
|---|---|---|---|
| `srv-luthier-shared/.../errors/GlobalExceptionHandler.java` | UPDATE | 1 | SH-03 |
| Todos os `*ServiceImpl`, `ServiceOrderEntity`, `StockConsumptionListener`, `UserServiceImpl`, `JwtConfig`, `AdminBootstrapConfig` com mensagem em inglês (lista na Task 1.1) | UPDATE | 1 | SH-03 |
| 17 entidades com `GenerationType.UUID` (lista na Task 1.2) | UPDATE | 1 | SH-04 |
| `srv-luthier-shared/src/test/.../validation/MasksTest.java` | CREATE | 1 | SH-05 |
| `srv-luthier-shared/src/test/.../errors/GlobalExceptionHandlerTest.java` | UPDATE | 1 | SH-05 (existe desde a fase 3) |
| `srv-luthier-shared/.../tenancy/TenantContext.java`, `src/test/.../TenantContextTest.java` | UPDATE | 1 | SH-07 |
| `srv-luthier-service/.../domain/repositories/ServiceOrderRepository.java` | UPDATE | 1 | SRV-15 (remove `sumPaid`) |
| `srv-luthier-app/src/test/java/com/luthier/RegistrationFlowTest.java` | UPDATE | 1 | APP-09 |
| `srv-luthier-app/src/test/java/com/luthier/UuidV7Test.java` | CREATE | 1 | SH-04 |
| `srv-luthier-security/.../configs/SecurityConfig.java` | UPDATE | 2 | SEC-13, APP-05 |
| `srv-luthier-security/.../services/impl/AuthServiceImpl.java` | UPDATE | 2 | SEC-14, SEC-21 |
| `srv-luthier-security/.../services/impl/LoginFailureThrottle.java` | CREATE | 2 | SEC-14 |
| `srv-luthier-security/.../jobs/TokenCleanupJob.java`, demais arquivos com `jakarta.transaction` | UPDATE | 2 | SEC-15 |
| `srv-luthier-security/src/main/resources/db/migration/security/V7_0__user_version_timestamps.sql` | CREATE | 2 | SEC-16 |
| `srv-luthier-security/.../domain/entities/UserEntity.java` | UPDATE | 2 | SEC-16 |
| `srv-luthier-security/.../domain/filters/RequestBodyLimitFilter.java` | CREATE | 2 | APP-05 |
| `srv-luthier-security/.../configs/SegurancaProperties.java` | UPDATE | 2 | APP-05 |
| `srv-luthier-app/src/main/resources/application-dev.yaml`, `srv-luthier-parent/pom.xml`, `CLAUDE.md` | UPDATE | 2 | SEC-18 |
| `srv-luthier-app/src/test/java/com/luthier/LowSecurityTest.java` | CREATE | 2 | SEC-13/14/16/21, APP-05 |
| `srv-luthier-app/src/test/java/com/luthier/CodingRulesArchitectureTest.java` | CREATE | 2 | SEC-15 |
| `srv-luthier-customer/src/main/resources/db/migration/customer/V7_1__customer_format_checks.sql` | CREATE | 3 | CUS-06 |
| `srv-luthier-customer/.../domain/entities/InstrumentEntity.java` | UPDATE | 3 | CUS-06 (`@Index`) |
| `srv-luthier-product/.../services/ProductService.java`, `impl/ProductServiceImpl.java`, `controllers/ProductController.java`, `domain/entities/ProductEntity.java`, `domain/repositories/ProductRepository.java`, `domain/records/StockMovementRequest.java` | UPDATE | 3 | PRO-05, PRO-06 |
| `srv-luthier-service/src/main/resources/db/migration/service/V7_2__service_catalog_title_key.sql` | CREATE | 3 | SRV-13 |
| `srv-luthier-service/.../domain/entities/ServiceCatalogEntity.java`, `domain/repositories/ServiceCatalogRepository.java`, `services/impl/ServiceCatalogServiceImpl.java` | UPDATE | 3 | SRV-13 |
| `srv-luthier-app/src/test/java/com/luthier/LowDataRulesTest.java` | CREATE | 3 | |
| `srv-luthier-service/src/main/resources/db/migration/service/V7_3__order_closed_at.sql` | CREATE | 4 | SRV-12 |
| `srv-luthier-service/.../domain/entities/ServiceOrderEntity.java` | UPDATE | 4 | SRV-12 (`closedAt`, `isPortalOpen`) |
| `srv-luthier-service/.../ServiceOrderStatusChanged.java` | UPDATE | 4 | SRV-12 (sai o `trackingToken`) |
| `srv-luthier-service/.../PortalLinks.java` | CREATE | 4 | SRV-12 (API pública) |
| `srv-luthier-service/.../services/impl/PortalLinksImpl.java` | CREATE | 4 | |
| `srv-luthier-service/.../services/impl/CustomerPortalServiceImpl.java`, `ServiceOrderServiceImpl.java` | UPDATE | 4 | SRV-12 |
| `srv-luthier-service/.../configs/PortalObservationConfig.java` | CREATE | 4 | SRV-12 (mascara o token nos spans) |
| `srv-luthier-notification/.../listeners/NotificationListeners.java` | UPDATE | 4 | SRV-12 (link via `PortalLinks`) |
| `srv-luthier-app/src/main/resources/application.yaml`, `README.md` | UPDATE | 4, 5 | Propriedades novas |
| `srv-luthier-app/src/test/java/com/luthier/PortalTokenLifetimeTest.java` | CREATE | 4 | |
| `srv-luthier-customer/src/main/resources/db/migration/customer/V7_4__customer_lifecycle_dates.sql` | CREATE | 5 | SRV-14 |
| `srv-luthier-customer/.../domain/entities/CustomerEntity.java`, `domain/repositories/CustomerRepository.java`, `services/impl/CustomerServiceImpl.java` | UPDATE | 5 | SRV-14 |
| `srv-luthier-customer/.../services/impl/CustomerAnonymizer.java`, `InactiveCustomerRetentionJob.java` | CREATE | 5 | SRV-14 |
| `srv-luthier-service/.../CustomerNotificationsExport.java` | CREATE | 5 | SRV-14 (SPI) |
| `srv-luthier-service/.../domain/records/CustomerDataExport.java`, `services/impl/PrivacyServiceImpl.java` | UPDATE | 5 | SRV-14 |
| `srv-luthier-notification/.../services/impl/CustomerNotificationsExportImpl.java` | CREATE | 5 | SRV-14 |
| `srv-luthier-notification/.../domain/repositories/NotificationRepository.java` | UPDATE | 5 | SRV-14 (`findAllByCustomerIdOrderByCreatedAtDesc`) |
| `srv-luthier-notification/.../services/impl/Notifier.java` | UPDATE | 5 | NOT-06 |
| `srv-luthier-app/src/test/java/com/luthier/PlatformScopeArchitectureTest.java` | UPDATE | 5 | Allowlist do job novo |
| `srv-luthier-app/src/test/java/com/luthier/CustomerRetentionTest.java` | CREATE | 5 | |
| `docker-compose.yml`, `.env.example`, `.gitignore` | UPDATE / CREATE | 6 | APP-06 |
| `.github/workflows/ci.yml`, `.github/dependabot.yml` | UPDATE | 6 | APP-07, APP-08 |
| `Dockerfile` | UPDATE | 6 | APP-08 |
| `docs/auditoria.md`, `docs/api.md`, `docs/openapi.json`, `docs/security-review.md`, `docs/correcoes.prd.md` | UPDATE | cada branch | Regra geral 6 do PRD |

## NOT Building

- **Confirmação de e-mail (SEC-05 completo)**: continua pendente de decisão, fora desta fase.
- **Renomear `user_tb.uuid` para `id` (SEC-16)**: quebraria as FKs da fase 5 (`fk_refresh_user`, `fk_reset_user`) e o JWT. Fica **aceito**, com justificativa.
- **`equals`/`hashCode` nas entidades (SH-04)**: não há `Set` de entidades. Fica aceito, com a regra registrada ("se um dia houver `Set`, implementar pelo id").
- **Unicidade do documento da luthieria (SEC-17)**: aceito por decisão.
- **Índice `lower(title)` só do PostgreSQL (SRV-13)**: substituído por coluna `title_key`, que funciona nos dois bancos.
- **Reativação de cliente**: o SRV-14 só anonimiza inativos; não há endpoint de reativar.
- **Log de acesso do proxy**: mascarar `/portal/orders/*` no proxy de borda fica **documentado** em `README.md`/`docs/security-review.md`; a app não grava log de acesso.
- **Atualizar a imagem para Temurin 24**: a branch do Dependabot `eclipse-temurin-24-jre-alpine` não é LTS; o projeto é Java 21. Fixar o digest do 21.

---

## Step-by-Step Tasks

> Em cada task: **(RED)** teste falhando pelo motivo certo, **(GREEN)** correção, teste + `ModularityTest`. Antes do PR: `./mvnw -B -ntp verify`.
> Teste isolado: `./mvnw -pl srv-luthier-app -am test -Dtest=<Classe> -Dsurefire.failIfNoSpecifiedTests=false`.

### Branch 1: `fix/low-code-hygiene`

#### Task 1.1: SH-03, mensagens em pt-BR
- **ACTION**: traduzir todas as mensagens de exceção em inglês.
- **IMPLEMENT**:
  - `GlobalExceptionHandler`: `"Invalid Data"` → `"Dados inválidos"`; `"Parametros inválidos"` → `"Parâmetros inválidos"`.
  - Tabela de tradução (procure cada uma com `grep -rn '<texto>' --include=*.java srv-*/src/main`):

    | De | Para |
    |---|---|
    | `Customer not found!` | `Cliente não encontrado` |
    | `Instrument not found!` | `Instrumento não encontrado` |
    | `Instrument not found for this customer!` | `Instrumento não encontrado para este cliente` |
    | `Product not found!` | `Produto não encontrado` |
    | `Product not found: ` + id | `Produto não encontrado: ` + id |
    | `Notification not found!` | `Notificação não encontrada` |
    | `User not found!` / `User not found` | `Usuário não encontrado` |
    | `Order not found!` / `Service order not found!` | `OS não encontrada` |
    | `Service item not found!` | `Serviço da OS não encontrado` |
    | `Part not found!` | `Peça não encontrada` |
    | `Service not found!` / `Service not found in catalog!` | `Serviço não encontrado no catálogo` |
    | `Email already exists` | `E-mail já cadastrado` |
    | `CPF already exists` / `CPF already registered` | `CPF já cadastrado` |
    | `SKU already registered` | `SKU já cadastrado` |
    | `Barcode already registered` | `Código de barras já cadastrado` |
    | `Service already registered` | `Serviço já cadastrado no catálogo` |
    | `The secret must be at least 32 bytes long (HS256).` | `O segredo do JWT precisa ter pelo menos 32 bytes (HS256).` |
    | `The password must be at least 12 characters long.` | `A senha do admin precisa ter pelo menos 12 caracteres.` |
  - Mantenha `BadCredentialsException("Bad credentials")`: ela nunca chega ao cliente (o `AuthExceptionHandler` devolve uma resposta genérica).
- **GOTCHA**:
  - Os textos de PRO-05 (Task 3.2) substituem os de SKU/código de barras. Se a branch 3 entrar antes, mantenha a versão dela.
  - Testes não checam essas mensagens (conferido com `grep -rn -E 'not found|already|Invalid Data|Parametros' srv-*/src/test`). Mesmo assim, rode a suíte.
- **VALIDATE**: `grep -rnoh --include=*.java -E 'Exception\("[A-Z][a-z]+ (not found|already)' srv-*/src/main` volta vazio. `GlobalExceptionHandlerTest` (Task 1.3) cobre os textos do handler.

#### Task 1.2: SH-04, UUIDv7
- **ACTION**: nas 17 entidades, troque `@GeneratedValue(strategy = GenerationType.UUID)` por `@UuidGenerator(style = UuidGenerator.Style.VERSION_7)` (`import org.hibernate.annotations.UuidGenerator;`). Remova os imports que ficarem sem uso (`GeneratedValue`, `GenerationType`).
- **Entidades**: `CustomerEntity`, `InstrumentEntity`, `NotificationEntity`, `ProductEntity`, `StockMovementEntity`, `AuditLogEntity`, `PasswordResetTokenEntity`, `RefreshTokenEntity`, `TenantEntity`, `UserEntity`, `OrderCounterEntity`, `OrderPartEntity`, `OrderServiceItemEntity`, `OrderTimelineEntity`, `PaymentEntity`, `ServiceCatalogEntity`, `ServiceOrderEntity`, mais as criadas nas fases 3 e 6 (ex.: `StockConsumptionEntity`). Confira com `grep -rln "GenerationType.UUID" --include=*.java srv-*/src/main`.
- **GOTCHA**:
  - Nenhuma migration: a coluna continua `uuid`.
  - O `@Id` fica; só o gerador muda.
  - IDs antigos (v4) convivem com os novos.
  - **`equals`/`hashCode`: não implementar** (ver NOT Building). Registre a regra no javadoc de `TenantScopedEntity`: "se um dia uma entidade entrar num `Set`, implementar `equals`/`hashCode` pelo id".
- **TEST** (`UuidV7Test`): crie uma luthieria, um cliente e uma OS via API e confira `UUID.fromString(id).version() == 7` para os três.
- **VALIDATE**: falha antes (versão 4).

#### Task 1.3: SH-05, testes unitários do núcleo
- **IMPLEMENT**:
  - `MasksTest`: `"lucas.silveira@guitar.com"` → `"lu***@guitar.com"`; `"a@x.com"` → `"a***@x.com"`; `"@x.com"` → `"***@x.com"`; sem `@` → `"***"`; `null` → `null`.
  - `GlobalExceptionHandlerTest` (já existe, fase 3): acrescente `NotFoundException` → 404 com o `detail`; `ConflictException` → 409; `BadRequestException` → 400; `ObjectOptimisticLockingFailureException` → 409 com a mensagem fixa; corpo inválido (`MethodArgumentNotValidException` via MockMvc standalone com um controller de teste e `@Valid`) → 400 com `detail = "Dados inválidos"` e `errors`. Siga o setup standalone que o arquivo já usa.
  - `TenantContextTest`: confira se já cobre a restauração de `null` depois de `callAs` (fase 2). Se não cobrir, acrescente.
- **VALIDATE**: verde.

#### Task 1.4: SH-07, log quando o trecho roda sem tenant
- **ACTION**: em `TenantContext.within`, quando `tenantId == null && !platform`, `log.warn("Trecho executado sem tenant: modo restrito, nenhum dado de negócio será lido ou gravado (SH-07)")`. Use um `private static final Logger log = LoggerFactory.getLogger(TenantContext.class)` (o slf4j vem pelo `spring-boot-starter-data-jpa` do shared).
- **GOTCHA**:
  - Não logue o stack inteiro; uma linha basta.
  - Confira que nenhuma chamada legítima usa `callAs(null, ...)`: `grep -rn "callAs(\|runAs(" --include=*.java srv-*/src/main` (hoje só eventos e o portal).
- **TEST** (`TenantContextTest`): `ListAppender<ILoggingEvent>` do Logback preso ao logger `com.luthier.shared.tenancy.TenantContext`; `callAs(null, () -> 1)` gera um WARN; `callAs(UUID.randomUUID(), ...)` e `callAsPlatform(...)` não geram.
- **VALIDATE**: falha antes (nenhum log).

#### Task 1.5: SRV-15 e APP-09
- **SRV-15**: apague `sumPaid()` de `ServiceOrderRepository` (linha ~100: `select coalesce(sum(o.paidAmount), 0) ...`). Antes, confirme com `grep -rn sumPaid --include=*.java` que nada usa.
- **APP-09**: em `RegistrationFlowTest`, troque `register(...)` por `api.registerWorkshop(...)` e o `auth(...)` local por `ApiClient.auth` (import estático). Mantenha `createCustomer`/`customerJson`, que são específicos do teste (ou use `api.create(token, "/customers", customerJson(name))`).
- **VALIDATE**: compila e `RegistrationFlowTest` verde.

#### Task 1.6: docs
`docs/auditoria.md`: ✔ SH-03, SH-05, SRV-15, APP-09, SH-07 (registrar como novo); SH-04 ✔, com a nota "equals/hashCode aceito". PRD: mover SH-07 de "Achados surgidos" para a fase 7.

### Branch 2: `fix/low-security`

#### Task 2.1: SEC-13 + SEC-21, senha antes do "desativado" e recheck antes de emitir
- **IMPLEMENT**:
  - `SecurityConfig.authenticationManager`:
    ```java
    var provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    // SEC-13: confere a senha ANTES de dizer que a conta está desativada; senão a resposta
    // rápida revela pelo tempo quais e-mails existem e estão desativados.
    provider.setPreAuthenticationChecks(user -> { });
    provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
    ```
    (`org.springframework.security.authentication.AccountStatusUserDetailsChecker` confere locked/disabled/expired/credentials.)
  - `AuthServiceImpl.login` (SEC-21), depois do `authenticate`:
    ```java
    var user = userRepository.findByEmail(email)
        .filter(UserEntity::isActive)
        .filter(u -> tenantRepository.isActive(u.getTenantId()))
        .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
    ```
    Fecha a janela entre autenticar e emitir.
- **GOTCHA**: o `AuthExceptionHandler` já devolve a mesma resposta para qualquer `AuthenticationException`, então o contrato não muda.
- **TEST** (`LowSecurityTest`):
  - **SEC-13**: com o bean `AuthenticationManager`, usuário desativado + senha errada → `BadCredentialsException` (antes: `DisabledException`); desativado + senha certa → `DisabledException`.
  - **SEC-21**: `@MockitoSpyBean UserServiceImpl` devolve `UserDetails` habilitado para um usuário desativado no banco (simula a corrida). `POST /auth/login` → 401.
- **VALIDATE**: os dois falham antes.

#### Task 2.2: SEC-14, falhas de login agregadas
- **IMPLEMENT**: `LoginFailureThrottle` (`@Component` em `services/impl`):
  ```java
  /** SEC-14: quem tenta senhas com o e-mail de outra luthieria não enche a auditoria dela. */
  private final Cache<String, AtomicInteger> failures = Caffeine.newBuilder()
      .expireAfterWrite(Duration.ofMinutes(15)).maximumSize(100_000).build();
  /** @return o número da falha na janela, se ela deve ser auditada; vazio se não. */
  OptionalInt shouldRecord(UUID tenantId, String email) {
    int n = failures.get(tenantId + "|" + email, k -> new AtomicInteger()).incrementAndGet();
    return n == 1 || n % 50 == 0 ? OptionalInt.of(n) : OptionalInt.empty();
  }
  ```
  Em `AuthServiceImpl.login`, no `catch`: só publique `LOGIN_FAILED` quando `shouldRecord` devolver valor, com `details = Masks.email(email) + " · " + e.getClass().getSimpleName() + (n > 1 ? " · " + n + "ª falha em 15 min" : "")`. Sem tenant (e-mail inexistente), mantenha o comportamento atual (`tenantId` nulo não pertence a nenhuma luthieria).
- **GOTCHA**:
  - O Caffeine já é dependência do security (`RateLimitFilter`).
  - Por instância: com várias réplicas, o limite é por réplica. Documente.
- **TEST** (`LowSecurityTest`): 5 logins errados com o e-mail do dono → 1 linha `LOGIN_FAILED` no tenant (`JdbcTemplate`: `select count(*) from audit_log_tb where tenant_id = ? and action = 'LOGIN_FAILED'`). Antes: 5.
- **VALIDATE**: falha antes.

#### Task 2.3: SEC-15, só o `@Transactional` do Spring
- **ACTION**: troque todo `jakarta.transaction.Transactional` por `org.springframework.transaction.annotation.Transactional`, com `readOnly = true` nos métodos de leitura (`list`, `me` em `UserManagementServiceImpl`). Inclui `TokenCleanupJob`. Liste com `grep -rn "jakarta.transaction" --include=*.java srv-*/src/main`.
- **GOTCHA**: no `UserManagementServiceImpl` (depois do SEC-20 da fase 6), a anotação de classe pode ter virado `NOT_SUPPORTED` em `create`. Use `@Transactional(propagation = Propagation.NOT_SUPPORTED)` do Spring.
- **TEST** (`CodingRulesArchitectureTest`, ArchUnit):
  ```java
  noClasses().should().dependOnClassesThat().haveFullyQualifiedName("jakarta.transaction.Transactional")
      .because("SEC-15: use org.springframework.transaction.annotation.Transactional (readOnly, rollback do Spring)")
      .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.luthier"));
  ```
- **VALIDATE**: a regra falha antes e passa depois.

#### Task 2.4: SEC-16, `@Version` e timestamps em `user_tb`
- **IMPLEMENT**: `V7_0__user_version_timestamps.sql`:
  ```sql
  -- Fase 7, módulo security (SEC-16): edições concorrentes do usuário não se sobrescrevem.
  -- Pré-checagem: nenhuma (colunas novas com default).
  alter table user_tb add column version bigint default 0 not null;
  alter table user_tb add column created_at timestamp with time zone default current_timestamp not null;
  alter table user_tb add column updated_at timestamp with time zone default current_timestamp not null;
  ```
  `UserEntity`: `@Version private long version;`, `createdAt` (`updatable = false`) e `updatedAt`, com `@PrePersist`/`@PreUpdate` como em `TenantScopedEntity` (sem a regra de tenant, porque `user_tb` não tem filtro automático).
- **GOTCHA**:
  - Escreva `default ... not null` nessa ordem: H2 e PostgreSQL aceitam.
  - A PK continua `uuid` (ver NOT Building).
  - O `AdminBootstrap` cria usuário por `UserEntity.newUser`, e o `@PrePersist` cobre.
- **TEST** (`LowSecurityTest`): `PATCH /users/{id}/profile` incrementa `version` (`JdbcTemplate`, antes e depois). Duas alterações com a mesma versão carregada (`TransactionTemplate` + `update user_tb set version = version + 1` no meio) → `ObjectOptimisticLockingFailureException`.
- **VALIDATE**: falha antes (coluna inexistente).

#### Task 2.5: SEC-17 (aceito) e SEC-18
- **SEC-17**: só `docs/auditoria.md`: "Aceito (decisão de 2026-11-07): um dono pode ter várias oficinas com o mesmo CPF/CNPJ".
- **SEC-18**:
  - remova o bloco `spring.h2.console` de `application-dev.yaml`;
  - remova a dependência `spring-boot-h2console` de `srv-luthier-parent/pom.xml` (linhas ~46-48; o `h2` em si fica);
  - no `CLAUDE.md`, linha 22, troque "Swagger/api-docs + /h2-console on" por "Swagger/api-docs on";
  - no `README.md`, se houver menção, use `grep -n h2-console README.md`.
- **GOTCHA**: confirme que nenhum código importa classes do console (`grep -rn "h2console\|H2Console" --include=*.java`).
- **VALIDATE**: a app sobe em dev (`java -jar ... --spring.profiles.active=dev`) e `GET /h2-console` dá 401.

#### Task 2.6: APP-05, limite do corpo
- **IMPLEMENT**:
  - `SegurancaProperties`: novo componente `@DefaultValue("64KB") DataSize maxRequestBody` (`org.springframework.util.unit.DataSize`).
  - `RequestBodyLimitFilter extends OncePerRequestFilter`:
    - se `request.getContentLengthLong() > max`, responda **413** com `application/problem+json`, no mesmo formato do 429 do `RateLimitFilter` (`{"type":"about:blank","title":"Content Too Large","status":413,"detail":"Corpo da requisição maior que o permitido."}`);
    - senão, embrulhe a requisição num `HttpServletRequestWrapper` cujo `getInputStream()` conta os bytes e lança `IOException` ao passar do limite (cobre chunked). O Jackson converte em 400.
  - `SecurityConfig`: `.addFilterBefore(new RequestBodyLimitFilter(props.maxRequestBody().toBytes()), UsernamePasswordAuthenticationFilter.class)`, **antes** dos rate limits na ordem de registro.
  - `application.yaml`: `app.security.max-request-body: ${MAX_REQUEST_BODY:64KB}`; `README.md`: a variável.
- **GOTCHA**:
  - `getContentLengthLong()` é -1 em chunked; por isso o wrapper.
  - Não leia o corpo no filtro.
  - 64 KB cobre com folga a maior requisição legítima (OS com `reportedIssue` de 2000 caracteres e lista de serviços).
- **TEST** (`LowSecurityTest`): `POST /auth/login` com corpo de 100 KB (`"{\"email\":\"" + "a".repeat(100_000) + "\"}"`) → 413. Corpo normal → 401 (credencial errada), não 413.
- **VALIDATE**: falha antes (401).

#### Task 2.7: docs
`docs/auditoria.md`: ✔ SEC-13/14/15/16/18 e APP-05; SEC-17 aceito; SEC-21 registrado como novo e ✔; SEC-16 com a nota da PK. `docs/security-review.md`: limite de corpo, falhas de login agregadas. PRD: mover SEC-21 para a fase 7. `CLAUDE.md`: versão `V7_0`.

### Branch 3: `fix/low-data-rules`

#### Task 3.1: CUS-06, CHECKs de formato e índice de instrumentos
- **IMPLEMENT**: `V7_1__customer_format_checks.sql`:
  ```sql
  -- Fase 7, módulo customer (CUS-06).
  -- Antes do deploy, precisa dar 0:
  --   select count(*) from customer_tb where cpf is not null and length(cpf) <> 11;
  --   select count(*) from customer_tb where length(phone) not between 10 and 11;
  alter table customer_tb add constraint ck_customer_cpf_len check (cpf is null or length(cpf) = 11);
  alter table customer_tb add constraint ck_customer_phone_len check (length(phone) between 10 and 11);
  -- A listagem ordena por created_at desc. O índice novo vem ANTES de recriar a FK (no H2, a FK
  -- prende o primeiro índice compatível), como na V5_2.
  create index idx_instrument_tenant_customer_created
      on instrument_tb (tenant_id, customer_id, created_at desc);
  alter table instrument_tb drop constraint fk_instrument_customer;
  drop index idx_instrument_tenant_customer;
  alter table instrument_tb add constraint fk_instrument_customer
      foreign key (tenant_id, customer_id) references customer_tb (tenant_id, id);
  ```
  `InstrumentEntity`: troque o `@Index` `idx_instrument_tenant_customer` pelo novo (`columnList = "tenant_id, customer_id, created_at desc"`).
- **GOTCHA**:
  - O telefone anonimizado é `"00000000000"` (11 dígitos) e passa.
  - O `@Pattern` do `CustomerRequest` já aceita 10 ou 11 dígitos.
  - Se o `drop index` falhar no H2 por causa da FK, a ordem acima resolve; confirme o nome da FK em `V5_1`.
- **TEST** (`LowDataRulesTest`): `update customer_tb set cpf = '123'` via `JdbcTemplate` → `DataIntegrityViolationException`; o mesmo com `phone = '123'`.
- **VALIDATE**: falha antes.

#### Task 3.2: PRO-05, reativar produto
- **IMPLEMENT**:
  - `ProductRepository`: `Optional<ProductEntity> findBySku(String sku)`, `Optional<ProductEntity> findByBarcode(String barcode)`.
  - `ProductServiceImpl.create`/`update`: antes do `existsBySku…`, procure o dono do SKU. Se for **inativo**: `throw new ConflictException("O SKU pertence a um produto desativado (id " + p.getId() + "). Reative-o em POST /products/" + p.getId() + "/activate")`. Se for ativo: `"SKU já cadastrado"`. O mesmo para o código de barras.
  - `ProductEntity.activate()`; `ProductService.activate(UUID id)` com `@PreAuthorize(Access.STOCK_WRITE)`; a implementação usa `productRepository.findById(id)` (inclui inativos) → `NotFoundException("Produto não encontrado")` → `activate()` → `ProductResponse`. Audite com `AuditEvent.of("PRODUCT_ACTIVATED", "product", id, null)`, seguindo o padrão do `CUSTOMER_DEACTIVATED`.
  - `ProductController`: `@PostMapping("/{id}/activate") public ProductResponse activate(@PathVariable UUID id)`.
- **TEST** (`LowDataRulesTest`): cria SKU `X1`, `DELETE`, cria `X1` de novo → 409 com `detail` contendo o id; `POST /products/{id}/activate` → 200; `GET /products/{id}` → 200. ESTOQUISTA pode ativar e ATENDENTE recebe 403.
- **VALIDATE**: falha antes (rota inexistente, 404/405).

#### Task 3.3: PRO-06, `orderId` só na baixa automática
- **IMPLEMENT**: `StockMovementRequest.orderId` ganha `@Null(message = "orderId é preenchido só pela baixa automática da OS")`. `ProductServiceImpl.move` passa `null` no lugar de `request.orderId()`. A baixa da OS (`StockConsumptionListener`) continua passando o `orderId` direto ao `StockLedger`.
- **GOTCHA**: a FK com `tenant_id` do `stock_movement_tb.product_id` já veio na fase 5 (V5_2). Anote isso no PRO-06 da auditoria.
- **TEST**: `POST /products/{id}/movements` com `orderId` → 400; sem `orderId` → 201.
- **VALIDATE**: falha antes (201).

#### Task 3.4: SRV-13, título do catálogo sem diferença de maiúsculas
- **IMPLEMENT**: `V7_2__service_catalog_title_key.sql`:
  ```sql
  -- Fase 7, módulo service (SRV-13): "Regulagem" e "regulagem" são o mesmo serviço.
  -- Antes do deploy, precisa voltar vazio:
  --   select tenant_id, lower(title), count(*) from service_catalog_tb group by tenant_id, lower(title) having count(*) > 1;
  -- Atenção: em PostgreSQL com collation "C", lower() só converte ASCII. A aplicação grava a
  -- chave com toLowerCase(Locale.ROOT); confira títulos com maiúscula acentuada antes do deploy.
  alter table service_catalog_tb add column title_key varchar(120);
  update service_catalog_tb set title_key = lower(title);
  alter table service_catalog_tb alter column title_key set not null;
  alter table service_catalog_tb add constraint uk_service_tenant_title_key unique (tenant_id, title_key);
  alter table service_catalog_tb drop constraint uk_service_tenant_title;
  ```
  `ServiceCatalogEntity`: `@Column(name = "title_key", nullable = false, length = 120) private String titleKey;`, preenchido em `newService`/`update` com `title.toLowerCase(Locale.ROOT)`. Troque também a `@UniqueConstraint` da entidade. `ServiceCatalogRepository`: `existsByTitleKey`/`existsByTitleKeyAndIdNot` substituem os `IgnoreCase`.
- **TEST**: dois `INSERT` via `JdbcTemplate` no mesmo tenant com `title_key` igual → `DuplicateKeyException`/`DataIntegrityViolationException`. Pela API: "Regulagem" e depois "REGULAGEM" → 409.
- **VALIDATE**: o primeiro falha antes (coluna inexistente; antes, os títulos diferentes eram aceitos).

#### Task 3.5: docs
`docs/api.md`: `POST /products/{id}/activate` e o 400 do `orderId`. `docs/openapi.json`: rota nova e o campo `orderId` documentado como proibido. `docs/auditoria.md`: ✔ CUS-06, PRO-05, PRO-06, SRV-13. `CLAUDE.md`: versões.

### Branch 4: `change/srv-12-portal-token-lifetime`

#### Task 4.1: `closed_at` e expiração do portal (RED → GREEN)
- **IMPLEMENT**:
  - `V7_3__order_closed_at.sql`:
    ```sql
    -- Fase 7, módulo service (SRV-12): o link do portal vale até N dias depois de a OS fechar.
    -- Pré-checagem: nenhuma. OS já fechadas recebem a data da entrega ou da última alteração.
    alter table service_order_tb add column closed_at timestamp with time zone;
    update service_order_tb set closed_at = coalesce(delivered_at, updated_at)
     where status in ('ENTREGUE', 'CANCELADO');
    ```
  - `ServiceOrderEntity`: campo `closedAt`. Em `changeStatus`, quando `target` for `ENTREGUE` ou `CANCELADO`, `closedAt = now`.
    ```java
    /** SRV-12: o portal funciona com a OS aberta e até {@code ttl} depois de fechada. */
    public boolean isPortalOpen(Instant now, Duration ttl) {
      return closedAt == null || closedAt.plus(ttl).isAfter(now);
    }
    ```
  - `CustomerPortalServiceImpl.Actions.find`: depois do `findByTrackingToken`, `.filter(o -> o.isPortalOpen(Instant.now(), ttl))` e senão `NotFoundException("OS não encontrada")`. O `ttl` vem de `@Value("${luthieria.portal.closed-order-days:30}") int days`, injetado no construtor de `Actions`.
  - `application.yaml`: `luthieria.portal.closed-order-days: ${PORTAL_CLOSED_ORDER_DAYS:30}`; `README.md`: a variável.
- **TEST** (`PortalTokenLifetimeTest`): OS entregue (fluxo completo, como no `ServiceOrderFlowTest`) → portal 200; `update service_order_tb set closed_at = ? where id = ?` com 31 dias atrás → portal 404. O mesmo para uma OS cancelada. A unitária de `isPortalOpen` (em `ServiceOrderEntityTest`) cobre o limite exato.
- **VALIDATE**: falha antes (200).

#### Task 4.2: token fora do evento
- **IMPLEMENT**:
  - `com.luthier.service.PortalLinks` (pacote raiz, API pública):
    ```java
    /** Link do portal do cliente para outros módulos (SRV-12: o token não viaja em eventos). Sempre no tenant atual. */
    public interface PortalLinks { Optional<String> trackingToken(UUID orderId); }
    ```
  - `PortalLinksImpl` (`@Service`, `@Transactional(readOnly = true)`): `orderRepository.findById(orderId).map(ServiceOrderEntity::getTrackingToken)`.
  - `ServiceOrderStatusChanged`: remova o componente `trackingToken` e ajuste os dois `publish`/`publishStatus` (`ServiceOrderServiceImpl` e `CustomerPortalServiceImpl.Actions`).
  - `NotificationListeners.Handlers.message`: troque `e.trackingToken()` por `portalLinks.trackingToken(e.orderId()).map(t -> frontendUrl + "/acompanhar/" + t).orElse(frontendUrl)`. Injete `PortalLinks` no construtor de `Handlers` (o notification já pode depender de `service`).
- **GOTCHA**:
  - **Compatibilidade com o outbox:** eventos publicados antes do deploy têm o campo `trackingToken` no JSON. Se o `JsonMapper` do Modulith falhar com propriedade desconhecida, essas publicações nunca concluem. **Antes do deploy**, `select count(*) from event_publication where completion_date is null and event_type like '%ServiceOrderStatusChanged'` precisa dar 0. Na implementação, escreva um teste que desserializa um JSON antigo com o bean `org.springframework.modulith.events.core.EventSerializer` (`deserialize(json, ServiceOrderStatusChanged.class)`). Se falhar, anote `@JsonIgnoreProperties(ignoreUnknown = true)` no record (import `com.fasterxml.jackson.annotation.JsonIgnoreProperties`, que é o pacote de anotações mantido no Jackson 3).
  - O corpo da notificação continua com o link (é a função dela). A anonimização (fase 5/6) limpa o corpo.
- **TEST**: o `NotificationFlowTest` existente prova que o link continua no e-mail e no WhatsApp; rode-o. Mais o teste de desserialização acima.
- **VALIDATE**: compila sem `trackingToken` no evento; `NotificationFlowTest` verde.

#### Task 4.3: token fora dos spans
- **IMPLEMENT**: `com.luthier.service.configs.PortalObservationConfig`:
  ```java
  /** SRV-12: o path do portal carrega o token; o span HTTP (http.url) não pode levá-lo ao Jaeger. */
  @Bean
  ObservationFilter portalTokenMasking() {
    return context -> {
      var url = context.getHighCardinalityKeyValue("http.url");
      if (url != null && url.getValue().contains("/portal/orders/")) {
        context.addHighCardinalityKeyValue(KeyValue.of("http.url",
            url.getValue().replaceAll("/portal/orders/[^/?]+", "/portal/orders/{token}")));
      }
      return context;
    };
  }
  ```
- **GOTCHA**:
  - Confirme o nome da chave no Boot 4/Micrometer da versão do projeto: rode a app em dev com `TRACING_SAMPLING=1.0`, abra `/portal/orders/<token>` e veja no Jaeger (`docker compose up`) quais chaves o span traz. Se for `url.full`/`url.path` (convenção OpenTelemetry), mascare essas também.
  - O `ObservationFilter` precisa ser registrado no `ObservationRegistry`; no Boot ele é aplicado automaticamente como bean.
- **TEST**: unitário do bean com um `Observation.Context` contendo `http.url=/portal/orders/abc123/quote/approve` → `/portal/orders/{token}/quote/approve`.
- **VALIDATE**: verde. Validação manual no Jaeger registrada no relatório.

#### Task 4.4: docs
`docs/api.md`: o portal dá 404 depois de N dias da OS fechada. `README.md`/`docs/security-review.md`: o proxy deve mascarar `/portal/orders/*` no log de acesso; o token não vai em eventos nem em spans. `docs/auditoria.md`: ✔ SRV-12. `CLAUDE.md`: na lista de eventos, `ServiceOrderStatusChanged` sem token; a API pública `PortalLinks`; versões.

### Branch 5: `change/srv-14-lgpd-retention`

#### Task 5.1: datas de ciclo de vida do cliente
- **IMPLEMENT**: `V7_4__customer_lifecycle_dates.sql`:
  ```sql
  -- Fase 7, módulo customer (SRV-14): base da retenção de clientes inativos.
  -- Pré-checagem (informativa): select count(*) from customer_tb where active = false;
  alter table customer_tb add column deactivated_at timestamp with time zone;
  alter table customer_tb add column anonymized_at timestamp with time zone;
  update customer_tb set deactivated_at = updated_at where active = false;
  update customer_tb set anonymized_at = updated_at
   where active = false and name = 'Cliente anonimizado';
  create index idx_customer_deactivated on customer_tb (deactivated_at);
  ```
  `CustomerEntity`: campos `deactivatedAt`, `anonymizedAt`; `deactivate()` grava `deactivatedAt = Instant.now()`; `anonymize()` grava `anonymizedAt = now` e `deactivatedAt` se nulo.
- **GOTCHA**: o índice não começa por `tenant_id` de propósito: a consulta do job é de plataforma (todas as luthierias), como `idx_audit_created` (V5_0) e `idx_notification_created` (V4_2).

#### Task 5.2: job de anonimização de inativos (RED → GREEN)
- **IMPLEMENT**:
  - Extraia a anonimização de `CustomerServiceImpl.anonymize` para `CustomerAnonymizer` (`@Component` package-private em `services/impl`, `@Transactional`): busca por id, `customer.anonymize()`, scrub dos instrumentos (fase 6, Task 6.4), publica `CustomerAnonymized(TenantContext.require(), id)` e `AuditEvent`. `CustomerServiceImpl.anonymize` passa a delegar (o `@PreAuthorize(ADMIN)` continua na interface).
  - O `AuditEvent` do job leva `actor = "sistema: retenção"` (construtor de 6 argumentos da fase 6) e `details = "inativo há mais de N dias"`.
  - Record `RetentionCandidate(UUID tenantId, UUID customerId)` em `customer/domain/records`.
  - `CustomerRepository`:
    ```java
    /** Retenção (SRV-14): só dentro de TenantContext.callAsPlatform. */
    @Query("""
        select new com.luthier.customer.domain.records.RetentionCandidate(c.tenantId, c.id)
          from CustomerEntity c
         where c.active = false and c.anonymizedAt is null and c.deactivatedAt < :cutoff
        """)
    List<RetentionCandidate> findRetentionCandidates(@Param("cutoff") Instant cutoff, Pageable page);
    ```
  - `InactiveCustomerRetentionJob` em `customer/services/impl` (mesmo lugar do `NotificationRetentionJob` no módulo dele):
    ```java
    @Scheduled(cron = "0 0 4 * * *", zone = "America/Sao_Paulo")
    public void purge() {
      var cutoff = Instant.now().minus(retention);
      var candidates = TenantContext.callAsPlatform(() -> finder.candidates(cutoff, BATCH));   // leitura
      int done = 0;
      for (var c : candidates) {
        try {
          TenantContext.runAs(c.tenantId(), () -> anonymizer.anonymizeForRetention(c.customerId(), days));
          done++;
        } catch (RuntimeException e) {
          log.warn("Retenção de clientes: falha ao anonimizar {} ({})", c.customerId(), e.getClass().getSimpleName());
        }
      }
      log.info("Retenção de clientes: {} anonimizados", done);
    }
    ```
    `finder` é um bean interno `@Transactional(readOnly = true)`; `BATCH = 500` (via `PageRequest.of(0, BATCH)`). A retenção vem de `@Value("${luthieria.retention.inactive-customer-days:1825}")`.
  - `application.yaml`: `luthieria.retention.inactive-customer-days: ${RETENTION_INACTIVE_CUSTOMER_DAYS:1825}`; `README.md`.
  - `PlatformScopeArchitectureTest` (fase 6): acrescente `customer\\.services\\.impl\\.InactiveCustomerRetentionJob` ao regex da allowlist.
- **GOTCHA**:
  - Cliente inativo não tem OS aberta: o CUS-03 (fase 6) impede desativar com OS aberta, e a abertura de OS exige cliente ativo. Por isso o job não precisa checar OS.
  - Uma falha num cliente não para o lote (o `try/catch` acima), e o log leva só o id.
  - `TenantScopedEntity.getTenantId()` existe, e o campo `tenantId` pode ser usado no JPQL.
- **TEST** (`CustomerRetentionTest`):
  - luthieria A com cliente inativo cujo `deactivated_at` foi posto 6 anos atrás (`JdbcTemplate`), outro inativo recente e um ativo;
  - luthieria B com inativo antigo;
  - chame `job.purge()` direto (bean autowired): os dois antigos (A e B) ficam anonimizados (`anonymized_at` preenchido, nome "Cliente anonimizado"); o recente e o ativo ficam intactos;
  - `await` até os instrumentos do anonimizado ficarem sem série (efeito do evento).
- **VALIDATE**: falha antes (o job não existe).

#### Task 5.3: exportação com notificações
- **IMPLEMENT**:
  - `com.luthier.service.CustomerNotificationsExport` (pacote raiz do service, SPI):
    ```java
    /** LGPD (SRV-14): notificações enviadas ao titular, fornecidas por quem as guarda (notification). */
    public interface CustomerNotificationsExport {
      List<NotificationExport> forCustomer(UUID customerId);
      record NotificationExport(String channel, String status, String recipient, String subject,
                                String body, Instant createdAt, Instant sentAt) {}
    }
    ```
  - `CustomerNotificationsExportImpl` no notification (`@Service`, `@Transactional(readOnly = true)`): `repository.findAllByCustomerIdOrderByCreatedAtDesc(customerId)` mapeado (`channel`/`status` pelos `getValue()` dos enums).
  - `CustomerDataExport` ganha `List<CustomerNotificationsExport.NotificationExport> notifications`. `PrivacyServiceImpl` recebe `List<CustomerNotificationsExport>` (vazia se o módulo não estiver presente) e concatena os resultados.
- **GOTCHA**:
  - **Ciclo de módulos:** o service não pode depender do notification. A interface fica no service e o notification implementa (notification → service é permitido). O `ModularityTest` confirma.
  - O notification já tem índice por `customer_id` (V5_4).
- **TEST**: em `CustomerRetentionTest` (ou `PrivacyAndSecurityTest`): cliente com OS que mudou de status (gera e-mail/WhatsApp) → `await` → a exportação traz `notifications` com pelo menos 1 item.
- **VALIDATE**: falha antes (campo ausente).

#### Task 5.4: NOT-06 (métrica depois do commit) e NOT-05 (verificação)
- **NOT-06**: em `Notifier`, o incremento do contador vira:
  ```java
  private void countAfterCommit(String channel, String status) {
    Runnable inc = () -> metrics.counter("luthieria.notifications", "channel", channel, "status", status).increment();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { inc.run(); }
      });
    } else {
      inc.run();
    }
  }
  ```
  Use-o no `save` e no `recordResult` (fase 6).
- **NOT-05**: a fase 6 (Tasks 5.2 e 5.3) já trocou `getMessage()` pela classe da exceção. Confira com `grep -rn "getMessage()" --include=*.java srv-luthier-notification/src/main`, que precisa voltar vazio. Se sobrar algo, troque por `getClass().getSimpleName()`.
- **TEST** (NOT-06): num `TransactionTemplate` que salva uma notificação interna (via `Notifier.internal` dentro de `TenantContext.runAs`) e marca rollback (`status.setRollbackOnly()`), o contador `luthieria.notifications{channel=interno}` não muda (`MeterRegistry` autowired, leia antes e depois).
- **VALIDATE**: falha antes (o contador sobe mesmo com rollback).

#### Task 5.5: docs
`docs/api.md`: a exportação inclui `notifications`. `docs/security-review.md`: linha de retenção ("clientes inativos anonimizados após 5 anos"). `docs/auditoria.md`: ✔ SRV-14, NOT-05, NOT-06. `CLAUDE.md`: a SPI `CustomerNotificationsExport`, o job novo entre os que usam `callAsPlatform`, as versões. `README.md`: `RETENTION_INACTIVE_CUSTOMER_DAYS`.

### Branch 6: `chore/infra-hardening`

#### Task 6.1: APP-06, compose só em localhost
- **IMPLEMENT**: em `docker-compose.yml`:
  - todas as portas com `127.0.0.1:` (`"127.0.0.1:5432:5432"`, `"127.0.0.1:8025:8025"`, `"127.0.0.1:1025:1025"`, `"127.0.0.1:8080:8080"`, `"127.0.0.1:9090:9090"`, `"127.0.0.1:16686:16686"`, `"127.0.0.1:4318:4318"`);
  - **remova** `"8081:8081"` (o Prometheus raspa `app:8081` pela rede interna do compose, ver `ops/prometheus/prometheus.yml`);
  - segredos: `JWT_SECRET: ${JWT_SECRET:?defina JWT_SECRET no .env}`, `ADMIN_SENHA: ${ADMIN_SENHA:?...}`, `POSTGRES_PASSWORD`/`DB_PASSWORD: ${DB_PASSWORD:?...}`. É a pendência MÉDIA da revisão da fase 3;
  - crie `.env.example` com valores de exemplo e confira que `.env` está no `.gitignore` (acrescente se faltar).
- **VALIDATE**: `docker compose config` sem erro com um `.env` local; sem `.env`, falha com a mensagem. `docker compose up` e `curl http://127.0.0.1:8080/actuator/health` → 200. `README.md`: passo "copie `.env.example` para `.env`".

#### Task 6.2: APP-07, CI
- **IMPLEMENT** em `.github/workflows/ci.yml`:
  - remova `security-events: write` do job `dependency-scan` (fica só `contents: read`);
  - fixe cada action por SHA com comentário de tag: `uses: actions/checkout@<sha> # v4.x.y`. Pegue o SHA com `git ls-remote https://github.com/actions/checkout refs/tags/v4` (idem `actions/setup-java`, `actions/upload-artifact`, `aquasecurity/trivy-action` na tag `0.33.1`). Existem branches do Dependabot com versões novas (`checkout-7`, `setup-java-6`, `upload-artifact-7`, `trivy-action-0.36.0`): **mergeie-as antes** ou fixe direto nas versões delas; o Dependabot continua atualizando SHAs;
  - job novo `image-scan` (`needs: build`): `actions/setup-java` → `mvn -B -ntp install -DskipTests` → `docker build -t srv-luthieria:ci .` → `aquasecurity/trivy-action` com `scan-type: image`, `image-ref: srv-luthieria:ci`, `severity: HIGH,CRITICAL`, `ignore-unfixed: true`, `exit-code: "1"`.
- **GOTCHA**: o CI usa `mvn` e não `./mvnw`. Mantenha, para não mudar de escopo.
- **VALIDATE**: o CI do PR passa com os três jobs. Se o `image-scan` acusar CVE da imagem base, o APP-08 (digest novo) ou um `.trivyignore` justificado resolvem; registre no relatório.

#### Task 6.3: APP-08, imagem por digest
- **IMPLEMENT**: `docker buildx imagetools inspect eclipse-temurin:21-jre-alpine` → `FROM eclipse-temurin:21-jre-alpine@sha256:<digest>` (com a tag mantida para leitura). O Dependabot (ecossistema `docker`, já configurado) atualiza o digest.
- **GOTCHA**: feche/ignore a branch `dependabot/docker/eclipse-temurin-24-jre-alpine` (Java 24 não é LTS). Acrescente ao `dependabot.yml`, no ecossistema `docker`, um `ignore` de `eclipse-temurin` com `versions: [">= 22"]`.
- **VALIDATE**: `docker build .` funciona.

#### Task 6.4: docs
`docs/auditoria.md`: ✔ APP-06/07/08. `README.md`: `.env`, portas só em localhost.

---

## Testing Strategy

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `UuidV7Test` | Criar luthieria, cliente e OS | `version() == 7` | |
| `MasksTest` | E-mail normal, curto, `@` no início, sem `@`, null | Máscaras corretas | ✔ |
| `GlobalExceptionHandlerTest` (+) | Cada exceção | Status e `detail` em pt-BR | |
| `TenantContextTest` (+) | `callAs(null)` | 1 WARN; tenant e plataforma sem WARN | ✔ |
| `LowSecurityTest` | Desativado + senha errada; corrida no login; 5 falhas; versão do usuário; corpo de 100 KB | BadCredentials; 401; 1 auditoria; versão incrementa; 413 | ✔ |
| `CodingRulesArchitectureTest` | Classes de produção | Nenhuma usa `jakarta.transaction.Transactional` | |
| `LowDataRulesTest` | CPF/telefone fora do tamanho; SKU de inativo; `orderId` manual; título em outra caixa | Rejeita; 409 com id + reativação; 400; 409 | ✔ |
| `PortalTokenLifetimeTest` | OS fechada há 29 e 31 dias | 200 e 404 | ✔ (limite) |
| Teste do `ObservationFilter` | `http.url` com token | Token trocado por `{token}` | |
| `CustomerRetentionTest` | Inativos antigos/recentes, ativo, duas luthierias | Só os antigos anonimizados | ✔ multi-tenant |
| Teste de exportação | Cliente notificado | `notifications` não vazio | |
| Teste do NOT-06 | Notificação com rollback | Contador inalterado | ✔ |

### Edge Cases Checklist
- [x] Input vazio (`orderId` nulo; inativo sem `deactivated_at` não entra no job)
- [x] Input máximo (corpo de 100 KB; chunked sem `Content-Length`)
- [x] Tipos inválidos (CPF de 3 dígitos; telefone curto)
- [x] Concorrência (corrida no login SEC-21; `@Version` do usuário; títulos concorrentes via constraint)
- [x] Falha de rede (n/a; a falha de SMTP ficou na fase 6)
- [x] Permissão negada (ATENDENTE em `activate` → 403; portal expirado → 404)

---

## Validation Commands

### Static Analysis
```bash
./mvnw -B -ntp install -DskipTests
grep -rn "jakarta.transaction" --include=*.java srv-*/src/main              # vazio
grep -rn "GenerationType.UUID" --include=*.java srv-*/src/main              # vazio
grep -rn "getMessage()" --include=*.java srv-luthier-notification/src/main  # vazio
```

### Unit Tests
```bash
./mvnw -pl srv-luthier-shared test
./mvnw -pl srv-luthier-service -am test -Dtest=ServiceOrderEntityTest -Dsurefire.failIfNoSpecifiedTests=false
```

### Testes da fase
```bash
./mvnw -pl srv-luthier-app -am test -Dtest='UuidV7Test,LowSecurityTest,CodingRulesArchitectureTest,LowDataRulesTest,PortalTokenLifetimeTest,CustomerRetentionTest,PlatformScopeArchitectureTest,NotificationFlowTest,RegistrationFlowTest,ModularityTest' -Dsurefire.failIfNoSpecifiedTests=false
```

### Full Test Suite
```bash
./mvnw -B -ntp verify
```
EXPECT: verde em cada branch e numa worktree descartável com as 6 mergeadas.

### Database Validation (obrigatório: V7_0 a V7_4)
```bash
./mvnw -pl srv-luthier-app -am test -Dtest=PostgresMigrationTest -Dsurefire.failIfNoSpecifiedTests=false   # Docker ligado
```

### Manual Validation
- [ ] `docker compose up` com `.env`: portas só em `127.0.0.1` (`docker compose ps`), 8081 não publicada, Prometheus raspando a app.
- [ ] Jaeger: span de `GET /portal/orders/{token}` sem o token (Task 4.3).
- [ ] Dev: `/h2-console` → 401 e o `CLAUDE.md` não fala mais do console.
- [ ] CI do PR da branch 6: três jobs verdes, actions por SHA.

---

## Acceptance Criteria (do PRD, fase 7)
- [ ] Cada achado foi corrigido, ou aceito com a justificativa registrada em `docs/auditoria.md`: SEC-17 aceito; SH-04 (`equals`/`hashCode`) e SEC-16 (nome da PK) com a parte aceita registrada; SEC-13 e NOT-06 corrigidos.
- [ ] Infra (APP-06, APP-07, APP-08) validada: `docker compose up` só em localhost, CI com actions por SHA e Trivy varrendo a imagem.
- [ ] `./mvnw -B -ntp verify` verde e documentação alinhada (`docs/api.md`, `docs/openapi.json`, `docs/security-review.md`, `README.md`, `CLAUDE.md`).
- [ ] SH-07 e SEC-21 registrados em `docs/auditoria.md` e resolvidos; PRD atualizado.
- [ ] `PostgresMigrationTest` verde com Docker.

## Completion Checklist
- [ ] Mensagens novas em pt-BR (inclusive as desta fase)
- [ ] Jobs de plataforma: leitura em `callAsPlatform`, gravação em `runAs`, allowlist do SH-06 atualizada
- [ ] SPIs no pacote raiz certo, `ModularityTest` verde
- [ ] Migrations com cabeçalho e pré-checagem, H2 e PostgreSQL
- [ ] Nenhum dado pessoal novo em log (o job loga só ids e contagens)
- [ ] Propriedades novas no `application.yaml` e no `README.md` (`MAX_REQUEST_BODY`, `PORTAL_CLOSED_ORDER_DAYS`, `RETENTION_INACTIVE_CUSTOMER_DAYS`)
- [ ] Front avisado: mensagens em pt-BR, 413, `activate` de produto, 400 do `orderId`, portal expira, `notifications` na exportação

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Fase 6 não mergeada | Alta hoje | Alto | GATE 0 |
| Eventos antigos no outbox com `trackingToken` falham na desserialização | Média | Médio | Pré-checagem do outbox vazio + teste de desserialização + `@JsonIgnoreProperties` se preciso |
| Chave do span não é `http.url` no Boot 4 | Média | Baixo | Conferência no Jaeger (Task 4.3) e mascarar também `url.full`/`url.path` |
| `lower()` do PostgreSQL com collation "C" diverge do Java em maiúsculas acentuadas | Baixa | Baixo | Nota e consulta de conferência na V7_2 |
| `drop index` do instrumento preso à FK no H2 | Média | Baixo | Ordem da V5_2 (Task 3.1) |
| Job de retenção anonimiza quem não devia | Baixa | Alto (irreversível) | Condições estritas (`active = false`, `anonymized_at is null`, `deactivated_at < cutoff`), teste multi-tenant, lote de 500, prazo configurável; rodar a contagem antes do primeiro deploy: `select count(*) from customer_tb where active = false and deactivated_at < now() - interval '1825 days'` |
| SH-04 conflita com tudo | Média | Baixo | Branch 1 primeiro; a mudança é mecânica |
| Limite por réplica no SEC-14 | Certa com várias réplicas | Baixo | Documentado |

## Notes
- **Backfill de `deactivated_at`** usa `updated_at` dos inativos. Como `find` filtra ativos, um inativo não costuma ser editado depois; se for, a contagem reinicia, o que é conservador (nunca anonimiza antes do prazo).
- **SEC-13** deixa de ser "aceito": a correção é uma linha de configuração e não muda o contrato.
- **NOT-05** foi praticamente resolvido pela fase 6. Aqui fica só a verificação.
- Depois desta fase, todos os 65 achados da auditoria, mais os 5 surgidos, estão resolvidos ou aceitos. Faltam as validações de PostgreSQL e k6 das fases 2, 4 e 5.
