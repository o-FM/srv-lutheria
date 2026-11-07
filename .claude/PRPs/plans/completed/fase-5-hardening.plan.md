# Plan: Fase 5, migration de hardening (SEC-09, SEC-10, SEC-11, CUS-05, PRO-02, PRO-04, SRV-07, SRV-08, SRV-09, NOT-04)

## Summary
Fazer o banco garantir os invariantes que hoje só a aplicação garante, com uma migration por módulo (`V5_0` a `V5_4`), e as três mudanças de código que os achados exigem:
- **Constraints e índices:** CHECKs de domínio, FKs compostas `(tenant_id, …)` entre tabelas de negócio, FKs dos tokens, unicidade do recibo e índices que cobrem filtros e ordenações.
- **SEC-09:** o e-mail passa a ser gravado sempre em minúsculas e buscado por igualdade, o que usa o índice `uk_user_email`.
- **SEC-11:** o expurgo da auditoria apaga em lotes, uma transação por lote.
- **NOT-04:** a limpeza das notificações na anonimização vira um único `UPDATE`.

## User Story
Como dono de uma luthieria, quero que o banco recuse dados inválidos ou de outra luthieria mesmo quando o código erra, e que login, kanban, histórico de estoque e expurgos usem índices, para manter dados íntegros e respostas rápidas com o crescimento da base.

## Problem → Solution
| Achado | Hoje | Depois |
|---|---|---|
| SEC-09 | Login busca `lower(email) = lower(?)`, sem índice; a unicidade diferencia maiúsculas | E-mail sempre minúsculo (`UserEntity.normalizeEmail`), busca por igualdade, `ck_user_email_lower` |
| SEC-10 | Tokens sem FK para `user_tb`; limpeza por `expires_at` sem índice | `fk_refresh_user`, `fk_reset_user` (`on delete cascade`), `idx_refresh_expires`, `idx_reset_expires` |
| SEC-11 | Um `DELETE` gigante numa transação, sem índice em `created_at` | `idx_audit_created` e lotes de 5.000 (`luthieria.retention.audit-batch-size`), uma transação por lote |
| CUS-05 | `instrument_tb.customer_id → customer_tb(id)` não leva o tenant; o mesmo para as FKs filhas de produto e OS | FKs compostas `(tenant_id, x_id) → pai(tenant_id, id)`, com `uk_*_tenant_id` nos pais |
| PRO-02 | Histórico de movimentos ordena `created_at` sem índice | `idx_movement_tenant_product_created (tenant_id, product_id, created_at desc)` substitui `idx_movement_tenant_product` |
| PRO-04 | Só existe `current_quantity >= 0` | `ck_product_prices`, `ck_product_min_qty`, `ck_movement_qty` (**com AJUSTE = 0 permitido**, ver Notes) |
| SRV-07 | Sem CHECK monetário; FKs filhas sem tenant; OS sem FK para cliente e instrumento | `ck_order_money`, `ck_order_item_price`, `ck_order_part_price`, `ck_service_catalog_values`; FKs compostas nos filhos e em `customer_id`/`instrument_id` |
| SRV-08 | Recibo sem unicidade; `payment_tb.order_id` sem índice | `uk_payment_receipt (tenant_id, receipt_number)`, `idx_payment_order` |
| SRV-09 | Kanban filtra `status` e ordena `order_number desc` com índice `(tenant_id, status)` | `idx_order_tenant_status_number (tenant_id, status, order_number desc)` substitui o antigo |
| NOT-04 | `scrub` carrega todas as notificações do cliente e faz um UPDATE por linha; índice só em `customer_id` | `scrubByCustomerId` (um `update versioned`); `idx_notification_tenant_customer` substitui `idx_notification_customer` |

## Metadata
- **Complexity:** Large (cerca de 22 arquivos, 5 migrations, 3 mudanças de código, 3 testes novos ou ampliados)
- **Source PRD:** `docs/correcoes.prd.md`
- **PRD Phase:** Fase 5, Migration de hardening
- **Estimated Files:** 22

---

## Pré-requisito e branch

**Fase 3 mergeada antes do merge desta fase (bloqueante para o merge, não para o desenvolvimento).**
Em 2026-11-07, `develop` (`12f86a3`) já tem as fases 1, 2 e 4, mas **não** a fase 3 (`fix/srv-01-order-totals`, `fix/sh-02-integrity-409`, `fix/app-01-no-default-profile`, `fix/pro-01-idempotent-stock-consumption`). Motivos:
- **Flyway:** a fase 3 traz a `V4_7` (PRO-01). Se a `V5_x` for aplicada num banco antes da `V4_7`, a `V4_7` vira migration "fora de ordem" e o Flyway recusa a subida (`outOfOrder` é `false`). Em dev/H2 não aparece; em produção, sim.
- **SH-02:** sem ele, as violações das constraints novas viram 500 em vez de 409.
- **SRV-01:** o `ck_order_money` não depende dele, mas os dados de produção devem passar pela pré-checagem (ver Task 5) com as regras novas em vigor.

**Branch:** uma só, como pede o PRD (todas as migrations no mesmo PR): `fix/db-hardening`, a partir de `develop`. Se a fase 3 ainda não estiver mergeada quando começar, crie a partir de `develop` e faça rebase depois do merge dela. Conflitos esperados com a fase 3, todos pequenos:
- `TenantRegistrationServiceImpl` e `UserManagementServiceImpl` (SH-02 mexe no `save`/`saveAndFlush`; aqui só muda `existsByEmailIgnoreCase` → `existsByEmail`, linhas 50 e 49).
- `ServiceOrderEntity` (SRV-01 mexe no corpo; aqui só na anotação `@Table`, linhas 44-47).
- `docs/auditoria.md` (marcas ✔ em linhas diferentes).

**Versões:** `V5_0` (security), `V5_1` (customer), `V5_2` (product), `V5_3` (service), `V5_4` (notification). A ordem importa: a `V5_3` referencia `uk_customer_tenant_id` e `uk_instrument_tenant_id`, criados na `V5_1`.

---

## UX Design

### Before / After
N/A: mudança interna. Nenhum contrato de API muda.

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `POST /auth/login`, `/auth/forgot-password` com e-mail em maiúsculas | Funciona (IgnoreCase) | Continua funcionando (normalizado antes da busca) | Teste novo garante |
| `POST /auth/register`, `POST /users` com e-mail em maiúsculas | Grava minúsculo (já normalizava) | Igual | `AdminBootstrap` passa a normalizar também |
| Escrita que viola uma constraint nova | Aceita | 409 problem+json (com SH-02) ou 500 (sem SH-02) | Só alcançável por bug de código ou corrida; as validações de entrada já barram |
| Expurgo de auditoria | Uma transação | N transações de até 5.000 linhas | Log final com o total |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `srv-luthier-*/src/main/resources/db/migration/**/V*.sql` | all | Nomes reais de tabelas, colunas, constraints e índices (resumo em "Schema atual") |
| P0 | `srv-luthier-app/src/test/java/com/luthier/TenantIsolationTest.java` | 155-197 | Teste de metadados de FK e inserção via `JdbcTemplate` esperando `DataIntegrityViolationException` |
| P0 | `srv-luthier-app/src/test/java/com/luthier/RetentionJobFlowTest.java` | all | Teste de job de retenção: 3 luthierias, `expire(...)`, `ReflectionTestUtils.invokeMethod(bean, "purge")`, `tenantOf(token)` |
| P0 | `srv-luthier-notification/src/main/java/com/luthier/notification/services/impl/NotificationRetentionJob.java` | all | Padrão do bean interno `Purger` com `@Transactional` (o SEC-11 copia) |
| P0 | `srv-luthier-security/src/main/java/com/luthier/security/jobs/AuditRetentionJob.java` | all | Job que o SEC-11 reescreve |
| P0 | `srv-luthier-security/src/main/java/com/luthier/security/services/impl/AuthServiceImpl.java` | 50-82 | Login (SEC-02: **sem** `@Transactional`, não mude isso) |
| P1 | `srv-luthier-security/src/main/java/com/luthier/security/domain/repositories/UserRepository.java` | all | `findByEmailIgnoreCase`/`existsByEmailIgnoreCase` saem |
| P1 | `srv-luthier-security/src/main/java/com/luthier/security/services/impl/UserServiceImpl.java` | 22-34 | `loadUserByUsername` (chamado pelo `authenticationManager` no login) |
| P1 | `srv-luthier-security/src/main/java/com/luthier/security/services/impl/PasswordRecoveryServiceImpl.java` | 47-57 | `requestReset` |
| P1 | `srv-luthier-security/src/main/java/com/luthier/security/configs/AdminBootstrap.java` | 41-56 | Grava o admin inicial com o e-mail da config, **sem normalizar** hoje |
| P1 | `srv-luthier-security/src/main/java/com/luthier/security/domain/entities/UserEntity.java` | 79-92 | `newUser(...)` |
| P1 | `srv-luthier-security/src/main/java/com/luthier/security/domain/repositories/AuditLogRepository.java` | all | `deleteOlderThan` sai |
| P1 | `srv-luthier-notification/src/main/java/com/luthier/notification/listeners/NotificationListeners.java` | 59-63, 147-150 | `on(CustomerAnonymized)` com `runAs` e `Handlers.scrubCustomer` |
| P1 | `srv-luthier-notification/src/main/java/com/luthier/notification/domain/entities/NotificationEntity.java` | 23-25, 92-96 | `@Table` e `scrub()` |
| P1 | `srv-luthier-notification/src/main/java/com/luthier/notification/domain/repositories/NotificationRepository.java` | all | `findAllByCustomerId` sai |
| P1 | `srv-luthier-product/src/main/java/com/luthier/product/services/impl/StockLedger.java` | 32-55 | Semântica de `quantity` por tipo de movimento (AJUSTE = saldo contado, pode ser 0) |
| P2 | `srv-luthier-app/src/test/java/com/luthier/PrivacyAndSecurityTest.java` | 74-106 | Teste da anonimização, que o NOT-04 amplia |
| P2 | `srv-luthier-app/src/test/java/com/luthier/RegistrationFlowTest.java` | all | Onde entra o teste de e-mail com maiúsculas |
| P2 | `srv-luthier-service/src/main/java/com/luthier/service/domain/entities/ServiceOrderEntity.java` | 38-47, 283 | `@Table` e formato do recibo `REC-%d-%02d` |
| P2 | `srv-luthier-product/src/main/java/com/luthier/product/domain/entities/StockMovementEntity.java` | 19-22 | `@Table` com o índice que muda de nome |

### Schema atual relevante (develop `12f86a3` + `V4_7` da fase 3)
- `user_tb(uuid pk, tenant_id, email varchar(255) not null, …)`, `uk_user_email unique (email)`.
- `refresh_token_tb(id, token_hash, user_id, family_id, expires_at, revoked_at, created_at)` e `password_reset_token_tb(id, token_hash, user_id, expires_at, used_at, created_at)`: **sem `tenant_id`**, sem FK.
- `audit_log_tb(id, tenant_id null, actor, action, …, created_at)`, só `idx_audit_tenant_created (tenant_id, created_at)`. **Sem filtro automático de tenant** e sem FK de tenant (de propósito).
- `customer_tb(id pk, tenant_id, …)`; `instrument_tb(…, customer_id, constraint fk_instrument_customer foreign key (customer_id) references customer_tb (id))`, `idx_instrument_tenant_customer (tenant_id, customer_id)`.
- `product_tb(id, tenant_id, cost_price numeric(12,2), sale_price numeric(12,2), current_quantity, min_quantity, …)`; `stock_movement_tb(…, product_id, type in ('ENTRADA','SAIDA','AJUSTE'), quantity, …, constraint fk_movement_product foreign key (product_id) references product_tb (id))`, `idx_movement_tenant_product (tenant_id, product_id)`.
- `service_catalog_tb(…, labor_price numeric(12,2) not null, estimated_days integer)`.
- `service_order_tb(id, tenant_id, order_number, customer_id, instrument_id, status, …, discount, paid_amount)`, `idx_order_tenant_status (tenant_id, status)`, `idx_order_tenant_customer (tenant_id, customer_id)`.
- Filhas da OS com `fk_order_item_order`, `fk_order_part_order`, `fk_order_timeline_order`, `fk_payment_order`, todas `foreign key (order_id) references service_order_tb (id)`. `ck_order_part_quantity check (quantity > 0)` já existe. `payment_tb` tem `ck_payment_amount check (amount > 0)` e `idx_payment_tenant_created`.
- `notification_tb(…, customer_id uuid)`, `idx_notification_customer (customer_id)`.
- Toda tabela de negócio já tem `fk_<tabela>_tenant → tenant_tb(id)` (fase 2, `V4_3` a `V4_6`). **Mantenha essas FKs**: o `TenantIsolationTest` exige uma FK `tenant_id → tenant_tb` em toda tabela com `tenant_id`.

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| H2 e índices de FK | Conferido no planejamento com H2 2.4.240 | Ao criar uma FK, o H2 **reaproveita um índice existente** cujas colunas começam pelas da FK e depois **recusa `drop index`** nele (`Index "…" belongs to constraint "…"`). Por isso, na `V5_2`, a troca de índice vem **antes** de recriar `fk_movement_product`. No schema atual, `idx_order_tenant_status`, `idx_movement_tenant_product` e `idx_notification_customer` não estão presos a constraint nenhuma (conferido). |
| HQL `update versioned` | Hibernate ORM 7.x, "HQL: update statements" | `update versioned Entity e set …` incrementa `@Version`. Atualizações em massa não passam pelo `@PreUpdate`, então `updatedAt` precisa ir no `set`. |
| Filtro de tenant em DML | `NotificationRetentionJob.java:14-20` (fase 2) | A restrição de `@TenantId` vale também para `delete`/`update` em HQL: sem tenant, o `DELETE` não apaga nada. O `scrubCustomer` roda dentro de `TenantContext.runAs(event.tenantId(), …)`, então o `UPDATE` em massa fica restrito à luthieria do evento. |
| Paginação em `@Query` | Spring Data JPA 4.x | `@Query` com parâmetro `Pageable` e retorno `List<T>` aplica `setMaxResults` sem consulta de contagem. |
| Flyway | Flyway docs, "outOfOrder" | Com `outOfOrder=false` (padrão), uma versão menor pendente depois de uma maior aplicada falha a validação. Motivo do pré-requisito da fase 3. |
| PostgreSQL DDL | PostgreSQL 17 docs | `create index` sem `concurrently` bloqueia escritas na tabela durante a criação; `concurrently` não roda dentro de transação, e o Flyway roda cada migration em transação. Aceitável no tamanho atual (ver Risks). |

Verificação local feita no planejamento: as 5 migrations das Tasks 2 a 6, exatamente como escritas aqui, rodaram em sequência no H2 2.4.240 (`MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`) sobre todas as migrations de `develop` + a `V4_7`, sem erro.

---

## Patterns to Mirror

### MIGRATION (cabeçalho com achado, motivo e pré-checagem)
// SOURCE: srv-luthier-customer/src/main/resources/db/migration/customer/V4_3__tenant_fk.sql:1-9
```sql
-- APP-03: toda linha de negócio pertence a uma luthieria que existe. Barra o tenant ROOT
-- (visão de plataforma) e UUIDs inventados, a última defesa do isolamento (SH-01).
-- Antes do deploy, por tabela: select count(*) from <tabela> where tenant_id not in
-- (select id from tenant_tb); precisa dar 0, senão a migration falha. Não usamos NOT VALID
-- porque o H2 (dev e testes) não aceita a sintaxe.
alter table customer_tb
    add constraint fk_customer_tenant foreign key (tenant_id) references tenant_tb (id);
```
- Arquivo: `V<versão>__<descrição_em_snake>.sql` na pasta do módulo dono da tabela.
- Nomes: `ck_<tabela_curta>_<regra>`, `fk_<tabela_curta>_<pai>`, `uk_<tabela_curta>_<colunas>`, `idx_<tabela_curta>_<colunas>`.

### JOB COM BEAN INTERNO TRANSACIONAL
// SOURCE: srv-luthier-notification/src/main/java/com/luthier/notification/services/impl/NotificationRetentionJob.java:22-56
```java
@Component
class NotificationRetentionJob {
  private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);
  private final Purger purger;
  private final Duration retention;

  NotificationRetentionJob(Purger purger,
      @Value("${luthieria.retention.notification-days:365}") int days) {
    this.purger = purger;
    this.retention = Duration.ofDays(days);
  }

  @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
  public void purge() {
    var cutoff = Instant.now().minus(retention);
    int removed = TenantContext.callAsPlatform(() -> purger.deleteOlderThan(cutoff));
    log.info("Retenção: {} notificações removidas", removed);
  }

  @Component
  static class Purger {
    private final NotificationRepository repository;
    Purger(NotificationRepository repository) { this.repository = repository; }

    @Transactional
    public int deleteOlderThan(Instant cutoff) {
      return repository.deleteOlderThan(cutoff);
    }
  }
}
```

### REPOSITORY (@Modifying com JPQL)
// SOURCE: srv-luthier-security/src/main/java/com/luthier/security/domain/repositories/RefreshTokenRepository.java:29-41
```java
  @Modifying(flushAutomatically = true)
  @Query("update RefreshTokenEntity t set t.revokedAt = :now "
      + "where t.userId = :userId and t.revokedAt is null")
  int revokeAllByUser(@Param("userId") UUID userId, @Param("now") Instant now);

  @Modifying
  @Query("delete from RefreshTokenEntity t where t.expiresAt < :cutoff")
  int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
```

### NORMALIZAÇÃO DE E-MAIL (já existente nos cadastros)
// SOURCE: srv-luthier-security/src/main/java/com/luthier/security/services/impl/TenantRegistrationServiceImpl.java:48-50
```java
    var email = owner.email().strip().toLowerCase(Locale.ROOT);
    ...
    if (userRepository.existsByEmailIgnoreCase(email)) {
```

### LOGGING_PATTERN
// SOURCE: srv-luthier-security/src/main/java/com/luthier/security/jobs/AuditRetentionJob.java:17, 32
```java
  private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);
  ...
    log.info("Retenção: {} registros de auditoria removidos", removed);
```

### ERROR_HANDLING
Violação de constraint chega como `DataIntegrityViolationException`. Com o SH-02 (fase 3), o `GlobalExceptionHandler` responde 409 (`23xxx`) ou 400 (`22xxx`) sem detalhes. Nenhum tratamento novo nesta fase.

### TEST_STRUCTURE (constraint via JdbcTemplate)
// SOURCE: srv-luthier-app/src/test/java/com/luthier/TenantIsolationTest.java:47-81, 184-197
```java
@SpringBootTest(properties = "app.security.rate-limit.login.capacity=1000")
@AutoConfigureMockMvc
class TenantIsolationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  ...
  @BeforeEach
  void twoWorkshopsAndACustomer() throws Exception {
    var api = new ApiClient(mvc);
    var tokenA = api.registerWorkshop("Isolamento A");
    workshopA = tenantOf(tokenA);
    ...
  }
  ...
    assertThatThrownBy(() -> jdbc.update("""
            insert into customer_tb (id, tenant_id, name, phone, active, created_at, updated_at,
                                     version)
            values (?, ?, 'Órfão', '11987654321', true, ?, ?, 0)""",
        UUID.randomUUID(), UUID.fromString(tenantId), now, now))
        .isInstanceOf(DataIntegrityViolationException.class);
```
`tenantOf(token)` é `static` em `RetentionJobFlowTest` (`import static com.luthier.RetentionJobFlowTest.tenantOf;`).

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `srv-luthier-app/src/test/java/com/luthier/SchemaHardeningTest.java` | CREATE | Uma rejeição por constraint nova, controle positivo do AJUSTE 0, metadados de FK composta e de índices |
| `srv-luthier-security/src/main/resources/db/migration/security/V5_0__security_hardening.sql` | CREATE | SEC-09, SEC-10, SEC-11 |
| `srv-luthier-customer/src/main/resources/db/migration/customer/V5_1__customer_tenant_fk.sql` | CREATE | CUS-05 |
| `srv-luthier-product/src/main/resources/db/migration/product/V5_2__product_hardening.sql` | CREATE | CUS-05 (produto), PRO-02, PRO-04 |
| `srv-luthier-service/src/main/resources/db/migration/service/V5_3__service_hardening.sql` | CREATE | SRV-07, SRV-08, SRV-09, CUS-05 (OS) |
| `srv-luthier-notification/src/main/resources/db/migration/notification/V5_4__notification_customer_index.sql` | CREATE | NOT-04 (índice) |
| `srv-luthier-security/src/main/java/com/luthier/security/domain/entities/UserEntity.java` | UPDATE | `normalizeEmail` e uso em `newUser` |
| `srv-luthier-security/src/main/java/com/luthier/security/domain/repositories/UserRepository.java` | UPDATE | `findByEmail`, `existsByEmail` no lugar das versões IgnoreCase |
| `srv-luthier-security/src/main/java/com/luthier/security/services/impl/AuthServiceImpl.java` | UPDATE | Normaliza no login |
| `srv-luthier-security/src/main/java/com/luthier/security/services/impl/UserServiceImpl.java` | UPDATE | Normaliza em `loadUserByUsername` |
| `srv-luthier-security/src/main/java/com/luthier/security/services/impl/PasswordRecoveryServiceImpl.java` | UPDATE | Normaliza em `requestReset` |
| `srv-luthier-security/src/main/java/com/luthier/security/services/impl/TenantRegistrationServiceImpl.java` | UPDATE | `existsByEmail` |
| `srv-luthier-security/src/main/java/com/luthier/security/services/impl/UserManagementServiceImpl.java` | UPDATE | `existsByEmail` |
| `srv-luthier-security/src/main/java/com/luthier/security/domain/repositories/AuditLogRepository.java` | UPDATE | `findIdsOlderThan` e `deleteByIdIn` no lugar de `deleteOlderThan` |
| `srv-luthier-security/src/main/java/com/luthier/security/jobs/AuditRetentionJob.java` | UPDATE | Lotes, bean interno `Purger` |
| `srv-luthier-notification/src/main/java/com/luthier/notification/domain/repositories/NotificationRepository.java` | UPDATE | `scrubByCustomerId`; `findAllByCustomerId` sai |
| `srv-luthier-notification/src/main/java/com/luthier/notification/domain/entities/NotificationEntity.java` | UPDATE | Constante `SCRUBBED_BODY`; `scrub()` sai |
| `srv-luthier-notification/src/main/java/com/luthier/notification/listeners/NotificationListeners.java` | UPDATE | `scrubCustomer` usa o UPDATE único |
| `srv-luthier-product/src/main/java/com/luthier/product/domain/entities/StockMovementEntity.java` | UPDATE | `@Index` com o nome e as colunas novas |
| `srv-luthier-service/src/main/java/com/luthier/service/domain/entities/ServiceOrderEntity.java` | UPDATE | `@Index` com o nome e as colunas novas |
| `srv-luthier-app/src/main/resources/application.yaml` | UPDATE | `audit-batch-size: ${RETENTION_AUDIT_BATCH_SIZE:5000}` |
| `srv-luthier-app/src/test/java/com/luthier/RetentionJobFlowTest.java` | UPDATE | Teste do expurgo da auditoria em lotes (SEC-11) |
| `srv-luthier-app/src/test/java/com/luthier/RegistrationFlowTest.java` | UPDATE | Cadastro e login com maiúsculas (SEC-09) |
| `srv-luthier-app/src/test/java/com/luthier/UserEmailNormalizationTest.java` | CREATE | `newUser` normaliza o e-mail (SEC-09, `AdminBootstrap`) |
| `srv-luthier-app/src/test/java/com/luthier/PrivacyAndSecurityTest.java` | UPDATE | Anonimização só toca as notificações do cliente e da luthieria (NOT-04) |
| `README.md`, `CLAUDE.md`, `docs/auditoria.md` | UPDATE | Variável nova; lista de versões do Flyway; ✔ nos 10 achados |

## NOT Building

- **FKs entre módulos para `product_id` (em `service_order_part_tb`) e `catalog_id`:** a auditoria diz que podem continuar sem FK. O `order_id` de `stock_consumption_tb` e de `stock_movement_tb` também fica sem FK (o product não conhece o service).
- **FK `tenant_id` em `audit_log_tb`:** continua nula de propósito (login com e-mail desconhecido).
- **Papel de banco dedicado ao purge** (sugestão opcional do SEC-11): infraestrutura, fora do código.
- **CHECKs de formato de CPF e telefone e índice de instrumentos** (CUS-06): fase 7.
- **`check (last_number >= 0)` no contador de OS** (SRV-04): fase 6.
- **Escapar curingas na busca de produto** (PRO-03) e limpar o campo `error` na anonimização (CUS-02): fase 6.
- **`create index concurrently`** no PostgreSQL: incompatível com a migration transacional do Flyway e com o H2.

---

## Step-by-Step Tasks

### Task 1: teste vermelho do schema (`SchemaHardeningTest`)
- **ACTION:** criar `srv-luthier-app/src/test/java/com/luthier/SchemaHardeningTest.java`.
- **IMPLEMENT:**
  - `@SpringBootTest(properties = "app.security.rate-limit.login.capacity=1000")`, `@AutoConfigureMockMvc`; `@Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;`.
  - `@BeforeEach`: duas luthierias pela API (`tokenA`, `tokenB`, `tenantA = tenantOf(tokenA)`, `tenantB = tenantOf(tokenB)`). Na A: cliente, instrumento, produto (`initialQuantity: 5`), serviço de catálogo e uma OS aberta com o serviço (`api.create(tokenA, "/service-orders", …)` como em `ServiceOrderFlowTest.setUp`/`fullLifecycle`). Na B: um cliente e um instrumento. Leia os IDs que só existem no banco com `jdbc.queryForObject("select uuid from user_tb where tenant_id = ?", UUID.class, tenantA)` e `jdbc.queryForObject("select id from service_order_item_tb where order_id = ?", …)`.
  - Helper `assertRejected(String sql, Object... args)`: `assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataIntegrityViolationException.class);`.
  - Helper `now()`: `Timestamp.from(Instant.now())`.
  - Testes (um `@Test` por grupo, com `assertRejected` por constraint; nomes em inglês, como os existentes):
    1. `userEmailMustBeLowercase`: `update user_tb set email = 'Maiusculo@Teste.com' where uuid = ?`.
    2. `tokensMustBelongToAnExistingUser`: `insert into refresh_token_tb (id, token_hash, user_id, family_id, expires_at, created_at) values (?, ?, ?, ?, ?, ?)` com `user_id` aleatório e `token_hash` de 64 caracteres (`"a".repeat(64)`); o mesmo em `password_reset_token_tb (id, token_hash, user_id, expires_at, created_at)`.
    3. `childRowsCannotPointToAnotherWorkshop` (CUS-05/SRV-07): instrumento da B apontando para cliente da A (`insert into instrument_tb (id, tenant_id, customer_id, type, brand, model, created_at, updated_at, version) values (?, tenantB, customerA, 'GUITARRA', 'X', 'Y', ?, ?, 0)`); movimento da B para produto da A (`insert into stock_movement_tb (id, tenant_id, product_id, type, quantity, delta, balance_after, reason, created_at, updated_at, version) values (…, 'ENTRADA', 1, 1, 1, 'teste', …)`); item, peça, linha do tempo e pagamento da B apontando para a OS da A (colunas obrigatórias no "Schema atual"; em `service_order_timeline_tb` são `type, title, actor`; em `payment_tb`, `amount, method ('PIX'), receipt_number, received_by`); `update service_order_tb set customer_id = <cliente da B> where id = <OS da A>` e o mesmo com `instrument_id`.
    4. `moneyAndQuantitiesCannotBeNegative` (PRO-04/SRV-07): `update product_tb set cost_price = -1`, `sale_price = -1`, `min_quantity = -1`; `update service_order_tb set discount = -1`, `paid_amount = -1`; `update service_order_item_tb set labor_price = -1`; `update service_catalog_tb set labor_price = -1`, `estimated_days = 0`; insert de peça com `unit_price = -1`.
    5. `stockMovementQuantityRules` (PRO-04): `ENTRADA` e `SAIDA` com `quantity = 0` rejeitados; **controle positivo:** `AJUSTE` com `quantity = 0` aceito (`jdbc.update` não lança, retorna 1). Use tenant e produto da **A**.
    6. `receiptNumberIsUniquePerWorkshop` (SRV-08): dois `insert into payment_tb` na OS da A com o mesmo `receipt_number = 'REC-1-99'`; o segundo é rejeitado.
    7. `foreignKeysBetweenWorkshopTablesIncludeTheTenant` (metadados, à prova de futuro): leia todas as FKs com `information_schema.referential_constraints` + `key_column_usage` (mesmo join de `TenantIsolationTest.java:164-179`, sem o filtro de coluna), devolvendo `constraint_name, fk.table_name, fk.column_name, pk.table_name`. Agrupe em Java por constraint. Para toda FK em que **a tabela filha e a tabela pai têm `tenant_id`** e o pai **não** é `tenant_tb`, exija que `tenant_id` esteja entre as colunas da FK. Inclua um controle positivo: a lista de FKs avaliadas contém `fk_instrument_customer` e `fk_payment_order`.
    8. `hardeningIndexesExist`: `select index_name from information_schema.indexes where table_schema = 'public'` contém `idx_refresh_expires`, `idx_reset_expires`, `idx_audit_created`, `idx_movement_tenant_product_created`, `idx_payment_order`, `idx_order_tenant_status_number`, `idx_notification_tenant_customer` e **não** contém `idx_movement_tenant_product`, `idx_order_tenant_status`, `idx_notification_customer`.
  - Nada a limpar depois: cada teste registra luthierias novas, e as linhas inválidas são rejeitadas. O controle positivo do AJUSTE grava uma linha a mais numa luthieria descartável.
- **MIRROR:** TEST_STRUCTURE.
- **IMPORTS:** `static com.luthier.RetentionJobFlowTest.tenantOf`, `static org.assertj.core.api.Assertions.*`, `org.springframework.dao.DataIntegrityViolationException`, `org.springframework.jdbc.core.JdbcTemplate`, `java.sql.Timestamp`, `java.time.Instant`, `java.util.*`, `java.util.stream.Collectors`, `com.luthier.support.ApiClient`.
- **GOTCHA:**
  - Com `DATABASE_TO_LOWER=TRUE`, os nomes no `information_schema` do H2 vêm em minúsculas (como no `TenantIsolationTest`).
  - Os inserts precisam de **todas** as colunas `not null`; um `null` faria o teste passar pelo motivo errado (violação de NOT NULL também é `DataIntegrityViolationException`). Para cada `assertRejected`, garanta que só o valor sob teste é inválido. Um jeito simples: para cada insert, rode antes a versão válida (com o pai da mesma luthieria) e confira que ela é aceita.
  - Todos os testes, exceto o controle positivo do AJUSTE 0, falham antes das migrations. Esse é o "vermelho".
- **VALIDATE:** `./mvnw -pl srv-luthier-app -am test -Dtest=SchemaHardeningTest -Dsurefire.failIfNoSpecifiedTests=false`: todos vermelhos, exceto o controle positivo do AJUSTE 0.

### Task 2: `V5_0__security_hardening.sql`
- **ACTION:** criar em `srv-luthier-security/src/main/resources/db/migration/security/`.
- **IMPLEMENT:**
  ```sql
  -- Fase 5 (hardening), módulo security.
  -- Antes do deploy, as consultas abaixo precisam dar 0 linha/0:
  --   SEC-09: select lower(email), count(*) from user_tb group by lower(email) having count(*) > 1;
  --   SEC-10: select count(*) from refresh_token_tb where user_id not in (select uuid from user_tb);
  --           select count(*) from password_reset_token_tb where user_id not in (select uuid from user_tb);

  -- SEC-09: e-mail sempre em minúsculas; o login busca por igualdade e usa uk_user_email.
  update user_tb set email = lower(email);
  alter table user_tb add constraint ck_user_email_lower check (email = lower(email));

  -- SEC-10: token sempre de um usuário existente; a limpeza por expiração usa índice.
  alter table refresh_token_tb
      add constraint fk_refresh_user foreign key (user_id) references user_tb (uuid) on delete cascade;
  alter table password_reset_token_tb
      add constraint fk_reset_user foreign key (user_id) references user_tb (uuid) on delete cascade;
  create index idx_refresh_expires on refresh_token_tb (expires_at);
  create index idx_reset_expires on password_reset_token_tb (expires_at);

  -- SEC-11: o expurgo filtra só por created_at, em todas as luthierias.
  create index idx_audit_created on audit_log_tb (created_at);
  ```
- **MIRROR:** MIGRATION.
- **GOTCHA:** `lower()` do banco e `toLowerCase(Locale.ROOT)` do Java podem divergir em letras fora do ASCII (por exemplo `İ`). Os e-mails reais são ASCII; a pré-checagem acusa qualquer colisão.
- **VALIDATE:** a aplicação sobe nos testes (o Flyway roda a migration); os testes 1, 2 e parte do 8 ficam verdes.

### Task 3: `V5_1__customer_tenant_fk.sql`
- **ACTION:** criar em `srv-luthier-customer/src/main/resources/db/migration/customer/`.
- **IMPLEMENT:**
  ```sql
  -- CUS-05: o instrumento só pode apontar para um cliente da mesma luthieria. A unicidade
  -- (tenant_id, id) é o alvo das FKs compostas, inclusive as da OS (V5_3, módulo service).
  -- Antes do deploy, precisa dar 0:
  --   select count(*) from instrument_tb i where not exists (select 1 from customer_tb c
  --     where c.id = i.customer_id and c.tenant_id = i.tenant_id);
  alter table customer_tb add constraint uk_customer_tenant_id unique (tenant_id, id);
  alter table instrument_tb add constraint uk_instrument_tenant_id unique (tenant_id, id);
  alter table instrument_tb drop constraint fk_instrument_customer;
  alter table instrument_tb
      add constraint fk_instrument_customer foreign key (tenant_id, customer_id)
      references customer_tb (tenant_id, id);
  ```
- **MIRROR:** MIGRATION.
- **GOTCHA:** o H2 reaproveita `idx_instrument_tenant_customer` para a FK nova. Não apague esse índice.
- **VALIDATE:** sobe; o caso do instrumento no teste 3 fica verde.

### Task 4: `V5_2__product_hardening.sql`
- **ACTION:** criar em `srv-luthier-product/src/main/resources/db/migration/product/`.
- **IMPLEMENT:**
  ```sql
  -- Fase 5 (hardening), módulo product.
  -- Antes do deploy, cada consulta precisa dar 0:
  --   CUS-05: select count(*) from stock_movement_tb m where not exists (select 1 from product_tb p
  --             where p.id = m.product_id and p.tenant_id = m.tenant_id);
  --   PRO-04: select count(*) from product_tb where cost_price < 0 or sale_price < 0 or min_quantity < 0;
  --           select count(*) from stock_movement_tb
  --             where not (quantity > 0 or (type = 'AJUSTE' and quantity = 0));

  -- CUS-05: o movimento só aponta para produto da mesma luthieria. PRO-02: o histórico ordena
  -- por created_at desc pelo índice. A troca de índice vem ANTES de recriar a FK: o H2 prende à
  -- FK o primeiro índice compatível e depois não deixa apagá-lo.
  alter table product_tb add constraint uk_product_tenant_id unique (tenant_id, id);
  alter table stock_movement_tb drop constraint fk_movement_product;
  create index idx_movement_tenant_product_created
      on stock_movement_tb (tenant_id, product_id, created_at desc);
  drop index idx_movement_tenant_product;
  alter table stock_movement_tb
      add constraint fk_movement_product foreign key (tenant_id, product_id)
      references product_tb (tenant_id, id);

  -- PRO-04: preços e mínimo não negativos. ENTRADA e SAIDA movem quantidade positiva; o AJUSTE
  -- grava o saldo contado no inventário, que pode ser 0 (StockLedger, StockMovementRequest).
  alter table product_tb add constraint ck_product_prices check (cost_price >= 0 and sale_price >= 0);
  alter table product_tb add constraint ck_product_min_qty check (min_quantity >= 0);
  alter table stock_movement_tb
      add constraint ck_movement_qty check (quantity > 0 or (type = 'AJUSTE' and quantity = 0));
  ```
- **MIRROR:** MIGRATION.
- **GOTCHA:** **não** use o `check (quantity <> 0)` sugerido na auditoria: ele recusaria um inventário com saldo zero, que é válido (`StockMovementRequest.java:12`). Ver Notes.
- **VALIDATE:** sobe; testes 3 (movimento), 4 (produto) e 5 verdes.

### Task 5: `V5_3__service_hardening.sql`
- **ACTION:** criar em `srv-luthier-service/src/main/resources/db/migration/service/`.
- **IMPLEMENT:**
  ```sql
  -- Fase 5 (hardening), módulo service.
  -- Antes do deploy, cada consulta precisa dar 0:
  --   SRV-07: select count(*) from service_order_tb where discount < 0 or paid_amount < 0;
  --           select count(*) from service_order_item_tb where labor_price < 0;
  --           select count(*) from service_order_part_tb where unit_price < 0;
  --           select count(*) from service_catalog_tb where labor_price < 0 or estimated_days <= 0;
  --   CUS-05/SRV-07, para cada filha (service_order_item_tb, service_order_part_tb,
  --   service_order_timeline_tb, payment_tb):
  --           select count(*) from <filha> f where not exists (select 1 from service_order_tb o
  --             where o.id = f.order_id and o.tenant_id = f.tenant_id);
  --           select count(*) from service_order_tb o where not exists (select 1 from customer_tb c
  --             where c.id = o.customer_id and c.tenant_id = o.tenant_id);
  --           select count(*) from service_order_tb o where not exists (select 1 from instrument_tb i
  --             where i.id = o.instrument_id and i.tenant_id = o.tenant_id);
  --   SRV-08: select tenant_id, receipt_number, count(*) from payment_tb
  --             group by tenant_id, receipt_number having count(*) > 1;

  -- SRV-07: valores monetários e prazos válidos.
  alter table service_order_tb
      add constraint ck_order_money check (discount >= 0 and paid_amount >= 0);
  alter table service_order_item_tb add constraint ck_order_item_price check (labor_price >= 0);
  alter table service_order_part_tb add constraint ck_order_part_price check (unit_price >= 0);
  alter table service_catalog_tb add constraint ck_service_catalog_values
      check (labor_price >= 0 and (estimated_days is null or estimated_days > 0));

  -- SRV-07/CUS-05: filhas da OS, cliente e instrumento sempre da mesma luthieria da OS.
  -- O cliente e o instrumento são do módulo customer (uk_*_tenant_id criados na V5_1); as FKs de
  -- tenant_id para tenant_tb (fase 2) já cruzam módulos do mesmo jeito.
  alter table service_order_tb add constraint uk_order_tenant_id unique (tenant_id, id);
  alter table service_order_item_tb drop constraint fk_order_item_order;
  alter table service_order_item_tb add constraint fk_order_item_order
      foreign key (tenant_id, order_id) references service_order_tb (tenant_id, id);
  alter table service_order_part_tb drop constraint fk_order_part_order;
  alter table service_order_part_tb add constraint fk_order_part_order
      foreign key (tenant_id, order_id) references service_order_tb (tenant_id, id);
  alter table service_order_timeline_tb drop constraint fk_order_timeline_order;
  alter table service_order_timeline_tb add constraint fk_order_timeline_order
      foreign key (tenant_id, order_id) references service_order_tb (tenant_id, id);
  alter table payment_tb drop constraint fk_payment_order;
  alter table payment_tb add constraint fk_payment_order
      foreign key (tenant_id, order_id) references service_order_tb (tenant_id, id);
  alter table service_order_tb add constraint fk_order_customer
      foreign key (tenant_id, customer_id) references customer_tb (tenant_id, id);
  alter table service_order_tb add constraint fk_order_instrument
      foreign key (tenant_id, instrument_id) references instrument_tb (tenant_id, id);

  -- SRV-08: recibo único por luthieria; carregar os pagamentos de uma OS usa índice.
  alter table payment_tb add constraint uk_payment_receipt unique (tenant_id, receipt_number);
  create index idx_payment_order on payment_tb (order_id);

  -- SRV-09: o kanban filtra por status e ordena por order_number desc.
  create index idx_order_tenant_status_number
      on service_order_tb (tenant_id, status, order_number desc);
  drop index idx_order_tenant_status;
  ```
- **MIRROR:** MIGRATION.
- **GOTCHA:**
  - `estimated_days > 0` segue o `@Positive Integer estimatedDays` de `ServiceCatalogRequest.java:15` (não `>= 0`, como diz a auditoria).
  - O recibo é `REC-<número da OS>-<sequência>` (`ServiceOrderEntity.java:283`). O número da OS é único por luthieria, então a unicidade não muda o comportamento atual.
  - As FKs de cliente e instrumento são as primeiras entre módulos de negócio (além das de tenant). O `ModularityTest` só olha código Java, não o schema.
- **VALIDATE:** sobe; testes 3, 4, 6, 7 e 8 verdes; `ServiceOrderFlowTest` verde.

### Task 6: `V5_4__notification_customer_index.sql`
- **ACTION:** criar em `srv-luthier-notification/src/main/resources/db/migration/notification/`.
- **IMPLEMENT:**
  ```sql
  -- NOT-04: toda consulta por cliente filtra também pela luthieria (filtro do @TenantId).
  create index idx_notification_tenant_customer on notification_tb (tenant_id, customer_id);
  drop index idx_notification_customer;
  ```
- **MIRROR:** MIGRATION.
- **VALIDATE:** `SchemaHardeningTest` inteiro verde.

### Task 7: anotações `@Index` das entidades
- **ACTION:** alinhar a documentação das entidades com o schema (o `ddl-auto: validate` não confere índices, então isso é só documentação).
- **IMPLEMENT:**
  - `StockMovementEntity.java:21`: `@Index(name = "idx_movement_tenant_product_created", columnList = "tenant_id, product_id, created_at desc")`.
  - `ServiceOrderEntity.java:45`: `@Index(name = "idx_order_tenant_status_number", columnList = "tenant_id, status, order_number desc")`.
- **GOTCHA:** `columnList` aceita `ASC`/`DESC` (JPA 2.1+).
- **VALIDATE:** compila; suíte verde.

### Task 8: SEC-09, e-mail normalizado (teste primeiro)
- **ACTION:** testes primeiro, depois o código.
- **IMPLEMENT:**
  - **Teste** `emailIsCaseInsensitiveAndStoredInLowercase` em `RegistrationFlowTest` (acrescente `@Autowired JdbcTemplate jdbc` se não existir):
    - `var email = "Dono-" + System.nanoTime() + "@Teste.COM";`, cadastro com `POST /auth/register` (JSON como em `ApiClient.registerWorkshop`) → 201.
    - Login com `email.toUpperCase(Locale.ROOT)` e com `email.toLowerCase(Locale.ROOT)` → 200 nos dois.
    - Novo cadastro com `email.toLowerCase(Locale.ROOT)` → 409.
    - `jdbc.queryForObject("select count(*) from user_tb where email = ?", Integer.class, email.toLowerCase(Locale.ROOT))` é 1 (gravado minúsculo).
    - Teste de **regressão** (o código atual já passa); o vermelho do SEC-09 é o `ck_user_email_lower` (teste 1 da Task 1) e o teste unitário abaixo.
  - **Teste unitário** sem Spring, `srv-luthier-app/src/test/java/com/luthier/UserEmailNormalizationTest.java` (o módulo security não tem `src/test`): `UserEntity.newUser(UUID.randomUUID(), "Admin", "  Admin@Luthieria.LOCAL ", null, null, "hash", Profile.ADMIN).getEmail()` é `"admin@luthieria.local"`. **Vermelho** antes: hoje o `AdminBootstrap` grava a config sem normalizar, e com o `ck_user_email_lower` um `ADMIN_EMAIL` com maiúscula derrubaria a subida.
  - **Código:**
    - `UserEntity`: `public static String normalizeEmail(String email) { return email == null ? null : email.strip().toLowerCase(Locale.ROOT); }` e, em `newUser`, `u.email = normalizeEmail(email);`.
    - `UserRepository`: troque `findByEmailIgnoreCase` → `Optional<UserEntity> findByEmail(String email);` e `existsByEmailIgnoreCase` → `boolean existsByEmail(String email);` (Javadoc de uma linha: "E-mail sempre minúsculo (SEC-09): a igualdade usa o índice uk_user_email.").
    - `AuthServiceImpl.login`: `var email = UserEntity.normalizeEmail(request.email());` e `findByEmail(email)` nas duas buscas.
    - `UserServiceImpl.loadUserByUsername`: `findByEmail(UserEntity.normalizeEmail(email))`.
    - `PasswordRecoveryServiceImpl.requestReset`: `findByEmail(UserEntity.normalizeEmail(request.email()))`.
    - `TenantRegistrationServiceImpl:48-50` e `UserManagementServiceImpl:46-49`: `var email = UserEntity.normalizeEmail(...)` e `existsByEmail(email)`.
    - `AdminBootstrap`: nada a mudar; `newUser` normaliza.
- **MIRROR:** NORMALIZAÇÃO DE E-MAIL.
- **IMPORTS:** `java.util.Locale` em `UserEntity`; `com.luthier.security.domain.entities.UserEntity` onde ainda não estiver; `com.luthier.security.domain.enums.Profile` no teste unitário.
- **GOTCHA:**
  - **Não** acrescente `@Transactional` no `login` (SEC-02).
  - Grep final: `grep -rn "IgnoreCase" srv-luthier-security/src/main` não pode achar nada de e-mail.
  - O JWT usa `user.getEmail()` como `sub`; com o e-mail já minúsculo, nada muda.
  - Sobra de `java.util.Locale` sem uso nos dois services depois da troca: remova o import.
- **VALIDATE:** `-Dtest='RegistrationFlowTest,UserEmailNormalizationTest,LoginConnectionPoolTest,RateLimitBypassTest'` verde.

### Task 9: SEC-11, expurgo da auditoria em lotes (teste primeiro)
- **ACTION:** teste em `RetentionJobFlowTest`, depois o código.
- **IMPLEMENT:**
  - **Teste** `auditPurgeDeletesInBatchesAcrossWorkshops` em `RetentionJobFlowTest`:
    - As `properties` do `@SpringBootTest` da classe passam a ser `{"app.security.rate-limit.login.capacity=1000", "luthieria.retention.audit-batch-size=2"}`.
    - Registre 3 luthierias (A e B antigas; C recente). O cadastro grava `TENANT_REGISTERED` em `audit_log_tb` por um listener assíncrono: `await()` até `auditCount(t) >= 1` para as três.
    - Para ter vários lotes, insira na A e na B mais 2 linhas cada: `jdbc.update("insert into audit_log_tb (id, tenant_id, actor, action, created_at) values (?, ?, 'teste', 'TESTE', ?)", UUID.randomUUID(), tenant, now)`.
    - `expireAudit(A)`, `expireAudit(B)`: `update audit_log_tb set created_at = ? where tenant_id = ?` com `Instant.now().minus(Duration.ofDays(2000))` (além dos 1825 dias padrão).
    - `ReflectionTestUtils.invokeMethod(context.getBean(AuditRetentionJob.class), "purge")` (classe pública em `com.luthier.security.jobs`).
    - Asserções: `auditCount(A)` e `auditCount(B)` são 0; `auditCount(C) >= 1`.
    - É teste de **regressão** do comportamento com lotes (o código antigo também apagaria tudo). O vermelho do SEC-11 é o índice (teste 8 da Task 1). Registre no relatório.
  - **Código** `AuditLogRepository` (remova `deleteOlderThan`, cujo único chamador era o job):
    ```java
    /** Ids de um lote do expurgo (SEC-11); o Pageable limita o tamanho do lote. */
    @Query("select a.id from AuditLogEntity a where a.createdAt < :cutoff")
    List<UUID> findIdsOlderThan(@Param("cutoff") Instant cutoff, Pageable batch);

    @Modifying
    @Query("delete from AuditLogEntity a where a.id in :ids")
    int deleteByIdIn(@Param("ids") Collection<UUID> ids);
    ```
  - **Código** `AuditRetentionJob`:
    ```java
    /**
     * Trilha de auditoria guardada por prazo definido (padrão 5 anos) e depois removida em lotes
     * (SEC-11): uma transação curta por lote, em vez de um DELETE gigante numa transação só.
     */
    @Component
    public class AuditRetentionJob {
      private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);
      private final Purger purger;
      private final Duration retention;
      private final int batchSize;

      public AuditRetentionJob(Purger purger,
          @Value("${luthieria.retention.audit-days:1825}") int days,
          @Value("${luthieria.retention.audit-batch-size:5000}") int batchSize) {
        if (batchSize < 1) {
          throw new IllegalArgumentException("luthieria.retention.audit-batch-size deve ser >= 1");
        }
        this.purger = purger;
        this.retention = Duration.ofDays(days);
        this.batchSize = batchSize;
      }

      @Scheduled(cron = "0 45 3 * * *", zone = "America/Sao_Paulo")
      public void purge() {
        var cutoff = Instant.now().minus(retention);
        long total = 0;
        int removed;
        do {
          removed = purger.deleteBatch(cutoff, batchSize);
          total += removed;
        } while (removed == batchSize);
        log.info("Retenção: {} registros de auditoria removidos", total);
      }

      /** Bean separado para que cada lote rode na própria transação (proxy do Spring). */
      @Component
      static class Purger {
        private final AuditLogRepository repository;
        Purger(AuditLogRepository repository) { this.repository = repository; }

        @Transactional
        public int deleteBatch(Instant cutoff, int size) {
          var ids = repository.findIdsOlderThan(cutoff, PageRequest.of(0, size));
          return ids.isEmpty() ? 0 : repository.deleteByIdIn(ids);
        }
      }
    }
    ```
  - `application.yaml`, em `luthieria.retention`: `audit-batch-size: ${RETENTION_AUDIT_BATCH_SIZE:5000}`.
  - `README.md:57`: a linha passa a listar também `RETENTION_AUDIT_BATCH_SIZE` ("Retenção LGPD; o expurgo da auditoria apaga em lotes de 5.000 linhas por padrão").
- **MIRROR:** JOB COM BEAN INTERNO TRANSACIONAL; LOGGING_PATTERN.
- **IMPORTS:** `org.springframework.data.domain.PageRequest`, `org.springframework.data.domain.Pageable`, `java.util.Collection`, `java.util.List`.
- **GOTCHA:**
  - `audit_log_tb` **não** é `TenantScopedEntity`: não precisa de `TenantContext.callAsPlatform`, e o expurgo vale para todas as luthierias sem nada a mais.
  - O laço termina quando um lote vem menor que o tamanho pedido.
  - O construtor público com um parâmetro de tipo package-private (`Purger`) compila; se o Checkstyle/IDE reclamar, deixe o construtor package-private (o Spring injeta igual).
  - O bean interno se chama `auditRetentionJob.Purger`, diferente do `notificationRetentionJob.Purger`. Não há colisão.
- **VALIDATE:** `-Dtest=RetentionJobFlowTest` verde (2 testes).

### Task 10: NOT-04, limpeza da anonimização num UPDATE (teste primeiro)
- **ACTION:** teste em `PrivacyAndSecurityTest`, depois o código.
- **IMPLEMENT:**
  - **Teste** `anonymizationScrubsOnlyThatCustomersNotificationsInThatWorkshop`:
    - Na luthieria do teste (`admin`), crie dois clientes, X e Y. Insira via `JdbcTemplate` uma notificação para cada (`insert into notification_tb (id, tenant_id, channel, status, recipient, subject, body, customer_id, created_at, updated_at, version) values (?, ?, 'WHATSAPP', 'PENDING', '11999990000', 'Assunto', 'Corpo', ?, ?, ?, 0)`), com o `tenant_id` de `tenantOf(admin)`.
    - Insira **outra** notificação em **outra** luthieria (`api.registerWorkshop("Outra")`) com `customer_id` = X (o banco aceita porque `notification_tb.customer_id` não tem FK).
    - `POST /customers/{X}/anonymize` → 204.
    - `await()` até a notificação de X ter `body = '[conteúdo removido a pedido do titular]'` e `recipient` nulo (via `jdbc`).
    - A de Y e a da outra luthieria continuam com `body = 'Corpo'` e `recipient` preenchido.
    - A de X tem `version = 1`.
    - É teste de **regressão** (o código atual também limpa só X na luthieria certa e incrementa a versão via entidade). O vermelho do NOT-04 é o índice (teste 8 da Task 1). Registre no relatório.
  - **Código** `NotificationEntity`: `public static final String SCRUBBED_BODY = "[conteúdo removido a pedido do titular]";` e remova `scrub()`.
  - **Código** `NotificationRepository` (remova `findAllByCustomerId`):
    ```java
    /**
     * Anonimização (LGPD, NOT-04): limpa num único UPDATE as notificações do cliente. Roda dentro
     * de TenantContext.runAs (NotificationListeners), então o filtro do @TenantId restringe à
     * luthieria do evento.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update versioned NotificationEntity n
           set n.recipient = null, n.body = :body, n.actionUrl = null, n.updatedAt = :now
         where n.customerId = :customerId
        """)
    int scrubByCustomerId(@Param("customerId") UUID customerId, @Param("body") String body,
        @Param("now") java.time.Instant now);
    ```
  - **Código** `NotificationListeners.Handlers.scrubCustomer`:
    ```java
    @Transactional
    public void scrubCustomer(CustomerAnonymized e) {
      repository.scrubByCustomerId(e.customerId(), NotificationEntity.SCRUBBED_BODY, Instant.now());
    }
    ```
- **MIRROR:** REPOSITORY (@Modifying).
- **IMPORTS:** `java.time.Instant` em `NotificationListeners` (confira se já existe).
- **GOTCHA:**
  - Se o parser HQL recusar `update versioned` ou o `set` de `updatedAt` (campo da superclasse `TenantScopedEntity`), use `set …, n.version = n.version + 1`. Registre o desvio.
  - O filtro de tenant em DML é o mesmo que o `NotificationRetentionJob` usa (fase 2). O teste com a outra luthieria prova isso.
  - O CUS-02 (fase 6) vai acrescentar a limpeza do campo `error`. Não faça agora.
- **VALIDATE:** `-Dtest=PrivacyAndSecurityTest` verde.

### Task 11: documentação e suíte
- **ACTION:**
  1. `CLAUDE.md:71`: a lista de versões passa a ser `` (current: `V1_0`…`V1_4`, `V2_0`, `V3_0`, `V4_0`…`V4_7`, `V5_0`…`V5_4`) ``. Se a fase 3 ainda não tiver entrado, escreva `V4_0`…`V4_6` e ajuste no rebase.
  2. `docs/auditoria.md`: ✔ no título dos 10 achados, como nas fases anteriores. No PRO-04, acrescente uma linha: "O CHECK sugerido (`quantity <> 0`) foi trocado por `quantity > 0 or (type = 'AJUSTE' and quantity = 0)`, porque o AJUSTE grava o saldo contado, que pode ser 0." No SRV-07, o mesmo para `estimated_days > 0`.
  3. `./mvnw -B -ntp verify`.
- **VALIDATE:** BUILD SUCCESS, `ModularityTest` e `TenantIsolationTest` verdes.

---

## Testing Strategy

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `SchemaHardeningTest.userEmailMustBeLowercase` | e-mail com maiúscula via SQL | rejeitado | |
| `SchemaHardeningTest.tokensMustBelongToAnExistingUser` | `user_id` inexistente | rejeitado (2 tabelas) | |
| `SchemaHardeningTest.childRowsCannotPointToAnotherWorkshop` | filha da B → pai da A (instrumento, movimento, item, peça, linha do tempo, pagamento, cliente e instrumento da OS) | rejeitado | Sim (isolamento) |
| `SchemaHardeningTest.moneyAndQuantitiesCannotBeNegative` | valores −1, `estimated_days = 0` | rejeitado | |
| `SchemaHardeningTest.stockMovementQuantityRules` | ENTRADA/SAIDA 0; AJUSTE 0 | rejeitado; **aceito** | Sim (controle positivo) |
| `SchemaHardeningTest.receiptNumberIsUniquePerWorkshop` | recibo repetido na mesma luthieria | rejeitado | |
| `SchemaHardeningTest.foreignKeysBetweenWorkshopTablesIncludeTheTenant` | metadados | toda FK entre tabelas com tenant inclui `tenant_id` | À prova de tabela nova |
| `SchemaHardeningTest.hardeningIndexesExist` | metadados | índices novos presentes; antigos ausentes | |
| `RegistrationFlowTest.emailIsCaseInsensitiveAndStoredInLowercase` | cadastro e login com maiúsculas | 201, 200, 200, 409; gravado minúsculo | |
| `UserEmailNormalizationTest` | `newUser` com espaços e maiúsculas | minúsculo e sem espaços | |
| `RetentionJobFlowTest.auditPurgeDeletesInBatchesAcrossWorkshops` | linhas antigas em 2 luthierias, lote 2 | antigas apagadas, recente mantida | Vários lotes |
| `PrivacyAndSecurityTest.anonymizationScrubsOnlyThatCustomersNotificationsInThatWorkshop` | notificações de X, Y e de outra luthieria com o mesmo `customer_id` | só a de X na luthieria certa é limpa, com versão incrementada | Sim (tenant em DML) |

### Edge Cases Checklist
- [x] AJUSTE com saldo 0 continua permitido
- [x] Lote vazio no expurgo (`ids.isEmpty()`): não chama o delete com `in ()`
- [x] `audit-batch-size` inválido: falha na subida (`IllegalArgumentException`)
- [x] E-mail com espaços nas pontas: `strip()`
- [x] Luthieria diferente com o mesmo `customer_id`: não é limpa
- [ ] PostgreSQL real: só com Docker

---

## Validation Commands

### Static Analysis
```bash
./mvnw -B -ntp -q compile
```
EXPECT: BUILD SUCCESS.

### Testes da fase
```bash
./mvnw -pl srv-luthier-app -am test -Dtest='SchemaHardeningTest,RegistrationFlowTest,UserEmailNormalizationTest,RetentionJobFlowTest,PrivacyAndSecurityTest,TenantIsolationTest,ServiceOrderFlowTest' -Dsurefire.failIfNoSpecifiedTests=false
```
EXPECT: todos verdes.

### Full Test Suite
```bash
./mvnw -B -ntp verify
```
EXPECT: BUILD SUCCESS.

### Database Validation (obrigatória pelo PRD nesta fase)
```bash
./mvnw -pl srv-luthier-app -am test -Dtest=PostgresMigrationTest -Dsurefire.failIfNoSpecifiedTests=false
```
EXPECT: com Docker ligado, 1 teste verde (não "skipped"). **Sem Docker, o critério do PRD fica em aberto**: registre no relatório e não marque a fase como concluída.

### Validação combinada com a fase 3 (enquanto ela não estiver mergeada)
Numa worktree descartável: `develop` + merge das 4 branches `fix/` da fase 3 + esta branch; `./mvnw -B -ntp verify`. Mesmo procedimento usado na fase 4.

### Manual Validation
- [ ] Rodar as consultas de pré-checagem (cabeçalho de cada migration) numa cópia do banco de produção: todas dão 0.
- [ ] `java -jar srv-luthier-app/target/srv-luthier-app-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev` e login com o admin de dev em maiúsculas (`ADMIN@luthieria.local`).

---

## Acceptance Criteria
- [ ] Cada CHECK, FK e índice dos 10 achados existe, com um teste de rejeição por constraint e o teste de metadados de FK composta e índices.
- [ ] As FKs compostas impedem que a filha de uma luthieria aponte para o pai de outra.
- [ ] SEC-09: e-mail gravado minúsculo, `ck_user_email_lower`, busca por igualdade; cadastro e login com maiúsculas funcionam.
- [ ] SEC-11: expurgo em lotes, uma transação por lote, teste com duas luthierias.
- [ ] NOT-04: um único UPDATE, restrito à luthieria do evento.
- [ ] Pré-checagens documentadas em cada migration.
- [ ] `verify` verde; `PostgresMigrationTest` verde com Docker.

## Completion Checklist
- [ ] Migrations na pasta do módulo dono, versões `V5_0` a `V5_4`, cabeçalho no padrão
- [ ] Nenhum `IgnoreCase` de e-mail restante
- [ ] Login continua sem `@Transactional`
- [ ] Logs no padrão "Retenção: {} …"
- [ ] Sem escopo extra (CUS-02, CUS-06, SRV-04, PRO-03 ficam para depois)
- [ ] `CLAUDE.md`, `README.md` e `docs/auditoria.md` atualizados

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Dados de produção violam uma constraint nova e a migration falha no deploy | Média | Alto | Pré-checagens no cabeçalho de cada migration, rodadas antes do deploy |
| Deploy da fase 5 antes da `V4_7` (fase 3): Flyway recusa a subida | Média | Alto | Pré-requisito explícito: merge da fase 3 antes |
| Migrations nunca rodaram no PostgreSQL (sem Docker) | Alta | Alto | `PostgresMigrationTest` obrigatório; a fase não fecha sem ele |
| `create index` bloqueia escritas durante a migration no PostgreSQL | Baixa (tabelas pequenas hoje) | Médio | Deploy fora do horário |
| `update versioned` ou o `set updatedAt` recusados pelo HQL | Baixa | Baixo | `n.version = n.version + 1` (Task 10) |
| Conflito de rebase com a fase 3 em 2 services de security e no `ServiceOrderEntity` | Média | Baixo | Linhas diferentes; resolução trivial |
| Algum fluxo legítimo grava movimento com `quantity` 0 fora do AJUSTE | Baixa | Médio | `StockLedger` já exige positivo em ENTRADA/SAIDA (`requirePositive`); a suíte cobre os fluxos de estoque |

## Notes
- **Desvio da auditoria no PRO-04:** `check (quantity <> 0)` está errado para este domínio. `StockMovementRequest.java:12` documenta "AJUSTE: quantity é o saldo contado no inventário (pode ser 0)", e `StockLedger` grava esse valor em `quantity`. A regra correta é `quantity > 0 or (type = 'AJUSTE' and quantity = 0)`.
- **Desvio da auditoria no SRV-07:** `estimated_days > 0` (a API exige `@Positive`), não `>= 0`.
- **Pegadinha do H2 descoberta no planejamento:** o H2 associa a uma FK nova o primeiro índice compatível e depois recusa `drop index` nele. A `V5_2` troca o índice antes de recriar a FK por isso. Se uma task futura recriar FKs, siga a mesma ordem.
- O teste de metadados de FK composta vale para tabelas futuras: uma FK nova entre tabelas com `tenant_id` sem levar o tenant quebra o build.
- Três testes são de regressão, não vermelhos (e-mail com maiúsculas no fluxo, lotes da auditoria, escopo da anonimização), porque o comportamento visível não muda. O vermelho de cada achado está no `SchemaHardeningTest` (constraint ou índice) e no `UserEmailNormalizationTest`.
- Confiança na primeira passada: 8/10. As migrations foram validadas no H2; o risco está no PostgreSQL (não validado) e no HQL do `update versioned`.
