# Plan: Fase 6, MÉDIOS restantes

## Summary
Corrige os 19 achados MÉDIOS da fase 6 de `docs/correcoes.prd.md`, mais os achados novos SEC-19 (ALTO, mesmo defeito do SEC-20, que ficou sem correção na fase 1), SEC-20 e SH-06. O trabalho fica em **7 branches coesas**, todas a partir de `develop` **depois do merge das fases 3 e 5**. Cada achado começa por um teste que falha antes da correção. Quatro migrations novas (`V6_0` a `V6_3`) ficam com versões reservadas neste plano.

## User Story
Como dono de uma luthieria que usa o sistema,
quero que a conta, os dados dos clientes, as OS e as notificações sigam regras consistentes e seguras,
para que ninguém trave a oficina, descubra dados mascarados ou receba e-mails duplicados, e para que a LGPD seja cumprida de fato.

## Problem → Solution
19 achados MÉDIOS abertos em `docs/auditoria.md`: rate limit contornável por IPv6, enumeração de e-mail pelo tempo de resposta, luthieria sem ADMIN, CPF reconstruível pela busca, anonimização incompleta, OS travada por peça inativa, e-mail duplicado pelo outbox, schema do outbox escrito à mão, entre outros. → Cada achado tem correção e teste de regressão. As decisões de negócio de SRV-05, SRV-06 e CUS-03 (2026-11-07) estão aplicadas, e a documentação (`docs/api.md`, `docs/openapi.json`, `docs/security-review.md`, `CLAUDE.md`, `README.md`) está alinhada.

## Metadata
- **Complexity**: XL (cerca de 45 arquivos em 7 branches)
- **Source PRD**: `docs/correcoes.prd.md`
- **PRD Phase**: Fase 6, MÉDIOS restantes
- **Estimated Files**: ~45 (≈ 25 de produção, ≈ 14 de teste, 4 migrations, docs)

---

## Decisões aplicadas (registradas no PRD em 2026-11-07)

| ID | Decisão | Efeito no código |
|---|---|---|
| SRV-05 | **A**: sair de `AGUARDANDO_APROVACAO` por `changeStatus` encerra o orçamento como `REJEITADO` | `ServiceOrderEntity.changeStatus` fecha o orçamento: `quoteStatus=REJEITADO`, `quoteAnsweredAt=now` e `rejectionReason` com o motivo, mais a linha do tempo `QUOTE_REJECTED`. Vale para `→ ORCAMENTO` e `→ CANCELADO` |
| SRV-06 | **A**: a baixa aceita produto inativo que já está na OS | `ProductQueries.findIncludingInactive` é usado em `requireStockFor`. O `Consumer` da baixa já usa `findById`, que não filtra `active` |
| CUS-03 | **A**: desativar cliente com OS aberta dá 409 | SPI `CustomerDeactivationGuard` no pacote raiz de customer, implementada no módulo service. Inverte a dependência sem ciclo |
| Recorte | Um plano único para a fase 6 | 7 branches, versões de migration reservadas |
| SEC-05 | A confirmação de e-mail continua **fora** (depende de decisão). Entra só a mitigação do PRD | O e-mail de boas-vindas deixa de incluir texto controlado pelo usuário |

---

## Pré-condição (GATE 0, obrigatório)

```bash
git fetch origin && git log --oneline origin/develop | head -30
```
`develop` precisa conter os merges de `fix/srv-01-order-totals`, `fix/sh-02-integrity-409`, `fix/app-01-no-default-profile`, `fix/pro-01-idempotent-stock-consumption` (fase 3) e `fix/db-hardening` (fase 5). Também precisa existir `srv-luthier-service/src/main/resources/db/migration/service/V5_3__service_hardening.sql`. **Se faltar algum, pare:** este plano depende de:
- `GlobalExceptionHandler` do SH-02 (409 para `DataIntegrityViolationException` e `ObjectOptimisticLockingFailureException`);
- `ServiceOrderEntity` do SRV-01 (`assertTotalsConsistent`, `acceptsPayment`);
- `StockConsumptionListener` idempotente (PRO-01);
- `UserEntity.normalizeEmail`, `UserRepository.findByEmail` e `NotificationRepository.scrubByCustomerId` (fase 5);
- as versões de migration até `V5_4`.

Os trechos "Patterns to Mirror" abaixo citam a versão **pós-merge** desses arquivos.

---

## UX Design

Internal change. A API muda de contrato em pontos específicos (tabela abaixo). Não há tela nova, e o front (`srv-lutheria-front`) precisa ser avisado.

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `PATCH /users/{id}/profile`, `POST /users/{id}/deactivate` | Aceita rebaixar ou desativar a si mesmo e o último ADMIN | **409** nesses casos | SEC-06 |
| `POST /auth/register`, `POST /users` | `document` com 15 a 18 dígitos ou `phone` com DDI → 500 | **400** com mensagem de validação | SEC-08. `document` aceita 11 dígitos (CPF válido) ou 14 (CNPJ válido); `phone` aceita 10 ou 11 dígitos |
| `GET /customers?search=` | CPF casa por pedaço | CPF casa **só com os 11 dígitos**. `%` e `_` são literais | CUS-01, CUS-04 |
| `GET /products?search=` | `%`/`_` funcionam como curinga | São literais | PRO-03 |
| `DELETE /customers/{id}` | Desativa mesmo com OS aberta | **409** se houver OS aberta | CUS-03 |
| `POST /service-orders/{id}/status` saindo de `aguardando_aprovacao` | `quoteStatus` fica `pendente` | `quoteStatus=rejeitado`, com `quoteAnsweredAt` e motivo | SRV-05 |
| `POST /service-orders/{id}/status` `pronto` com peça inativa | 409 "Peça não está mais no estoque" para sempre | Conclui e dá baixa | SRV-06 |
| `POST /auth/forgot-password` | Para e-mail ativo, responde só depois do SMTP | Responde logo. O e-mail sai depois do commit | SEC-04 |
| E-mail de boas-vindas | Inclui `ownerName` e `tenantName` | Texto fixo, sem dado do usuário | SEC-05 |
| `GET /notifications` | E-mail já nasce `enviado`/`falhou` | Pode aparecer `pendente` por instantes antes do envio | NOT-02 |
| `GET /audit-logs` → `actor` | E-mail completo ou `anonymousUser` | UUID do usuário, ou `anônimo` | SEC-07 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `CLAUDE.md` | all | Fronteiras de módulo, tenancy fail-closed, migrations globais |
| P0 | `docs/auditoria.md` | seções dos 19 achados | Texto oficial de cada problema |
| P0 | `srv-luthier-service/.../domain/entities/ServiceOrderEntity.java` | 229-278, 346-377 (pós-fase 3) | `changeStatus`, orçamento, invariantes |
| P0 | `srv-luthier-service/.../services/impl/ServiceOrderServiceImpl.java` | 150-175, 234-247, 281-285 | `changeStatus`, `requireStockFor`, `nextNumber` |
| P0 | `srv-luthier-security/.../services/impl/UserManagementServiceImpl.java` | all | SEC-06, SEC-20 |
| P0 | `srv-luthier-security/.../services/impl/AuthServiceImpl.java` | 51-83 | Padrão SEC-02 (hash fora de transação) que SEC-19/SEC-20 repetem |
| P0 | `srv-luthier-notification/.../listeners/NotificationListeners.java` | all | SEC-04, SEC-05, NOT-02 |
| P0 | `srv-luthier-notification/.../services/impl/Notifier.java` | all | NOT-02, CUS-02 (`error`) |
| P1 | `srv-luthier-product/.../services/impl/StockConsumptionListener.java` | all (pós-fase 3) | Padrão "runAs antes da transação + bean interno @Transactional" |
| P1 | `srv-luthier-notification/.../services/impl/NotificationRetentionJob.java` | all | Mesmo padrão com `callAsPlatform` |
| P1 | `srv-luthier-security/.../domain/filters/RateLimitFilter.java` | 97-124 | SEC-03 |
| P1 | `srv-luthier-security/.../services/impl/AuditServiceImpl.java` | all | SEC-07 |
| P1 | `srv-luthier-customer/.../services/impl/CustomerServiceImpl.java` | 111-162, 196-201 | CUS-01..04 |
| P1 | `srv-luthier-customer/.../domain/repositories/CustomerRepository.java` | 29-38 | Busca |
| P1 | `srv-luthier-product/.../domain/repositories/ProductRepository.java` | 53-65 | Busca |
| P1 | `srv-luthier-app/src/main/resources/db/migration/app/V1_1__event_publication.sql` | all | APP-02 |
| P2 | `srv-luthier-app/src/test/java/com/luthier/OrderListQueryCountTest.java` | all | Contagem de SQL com `Statistics` |
| P2 | `srv-luthier-app/src/test/java/com/luthier/RateLimitBypassTest.java` | all | Teste com capacidade real |
| P2 | `srv-luthier-app/src/test/java/com/luthier/support/ApiClient.java` | all | Helpers dos testes de fluxo |
| P2 | `srv-luthier-service/src/test/.../ServiceOrderEntityTest.java` | all (fase 3) | Teste de entidade sem Spring |
| P2 | `srv-luthier-app/src/test/java/com/luthier/TenantIsolationTest.java` | all | Teste de metadados e de `callAsPlatform` |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Outbox JPA do Spring Modulith 2.1.1 | Inspeção do jar local `~/.m2/.../spring-modulith-events-jpa/2.1.1` (javap em `JpaEventPublication`) | Colunas: `id` (UUID), `publication_date`, `listener_id`, `serialized_event`, `event_type`, `completion_date`, `last_resubmission_date`, `completion_attempts` (int), `status` (enum STRING). Os `String` não têm `length`. Consultas do repositório: `where completionDate is null order by publicationDate`, `where serializedEvent = ?1 and listenerId = ?2 and completionDate is null`, `delete ... where completionDate is not null`, `update ... where id = ?` |
| `ALTER COLUMN ... SET DATA TYPE` | Docs H2 2.x e PostgreSQL | A mesma sintaxe funciona nos dois. `varchar` sem tamanho é ilimitado no PostgreSQL (equivale a `text`) e vai até 1.000.000.000 no H2. **Não use `text`**: no H2 ele vira CLOB, e o `ddl-auto: validate` falha ("found clob, expecting varchar") |
| Hibernate 7: UPDATE em massa de entidade `@Immutable` | Guia de migração do Hibernate 7 (`hibernate.query.immutable_entity_update_query_handling_mode`) | O UPDATE HQL em `OrderTimelineEntity` (`@Immutable`) pode lançar exceção. Plano B na Task 6.4 |
| `@MockitoSpyBean` | Spring Framework 7 (`org.springframework.test.context.bean.override.mockito`) | Substitui o `@SpyBean` removido. Use para o `EmailSender` |
| Executor no Boot 4 | `TaskExecutionAutoConfiguration` | Declarar um bean `Executor`/`TaskExecutor` próprio pode desligar o `applicationTaskExecutor` padrão que o `@Async` usa. **O executor do SEC-04 não pode ser bean**: é um campo privado do componente |

---

## Patterns to Mirror

### NAMING_CONVENTION
```java
// SOURCE: srv-luthier-security/.../services/impl/UserManagementServiceImpl.java:25-41
@Service
@Transactional
public class UserManagementServiceImpl implements UserManagementService {
  private final UserRepository userRepository;
  ...
  public UserManagementServiceImpl(UserRepository userRepository, ...) { ... }
```
Interface em `services/` com `@PreAuthorize(Access.X)`, implementação em `services/impl/`, injeção por construtor, campos `final`. DTOs são records em `domain/records`. Comentários e mensagens de erro em pt-BR. Nomes de classe e método em inglês.

### ERROR_HANDLING
```java
// SOURCE: ServiceOrderEntity.java:340-344 (fase 3)
private void requireEditable() {
  if (!status.isEditable()) {
    throw new ConflictException("Itens só podem ser alterados antes do envio do orçamento");
  }
}
// SOURCE: UserManagementServiceImpl.java:113-116
return userRepository.findByUuidAndTenantId(id, TenantContext.require())
    .orElseThrow(() -> new NotFoundException("User not found!"));
```
Regra de negócio → `ConflictException` (409). Entrada inválida → `BadRequestException` ou Bean Validation (400). Recurso inexistente ou de outro tenant → `NotFoundException` (404). Nunca devolva mensagem do driver.

### LOGGING_PATTERN
```java
// SOURCE: NotificationListeners.java:87-90
} catch (RuntimeException e) {
  // Resposta do /auth/forgot-password é sempre 202; a falha fica só no log
  log.warn("Falha ao enviar e-mail de redefinição de senha: {}", e.getMessage());
}
```
SLF4J com `{}`. Nunca logue e-mail, CPF, token ou corpo de e-mail (use `Masks.email`). Para exceções de SMTP, logue a classe, não a mensagem: ela pode conter o destinatário (CUS-02).

### ASYNC_LISTENER_PATTERN (tenant antes da transação)
```java
// SOURCE: StockConsumptionListener.java:41-65, 67-102 (fase 3)
@Async
@TransactionalEventListener
public void on(StockConsumptionRequested event) {
  boolean applied = TenantContext.callAs(event.tenantId(), () -> consumer.consume(event));
  ...
}
@Component
static class Consumer {
  @Transactional
  public boolean consume(StockConsumptionRequested event) { ... }
}
```
Todo listener assíncrono novo (CUS-02 no módulo service) segue isto: `runAs`/`callAs` por fora e bean interno `@Transactional` por dentro.

### REPOSITORY_PATTERN (UPDATE em massa com tenant)
```java
// SOURCE: NotificationRepository.java (fase 5, NOT-04)
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("""
    update versioned NotificationEntity n
       set n.recipient = null, n.body = :body, n.actionUrl = null, n.updatedAt = :now
     where n.customerId = :customerId
    """)
int scrubByCustomerId(@Param("customerId") UUID customerId, @Param("body") String body,
    @Param("now") java.time.Instant now);
```
JPQL sobre entidade com `@TenantId`: o Hibernate aplica o filtro de tenant também no UPDATE. Use `update versioned` e `updatedAt = :now` em entidades `TenantScopedEntity`.

### SERVICE_PATTERN (hash fora de transação, SEC-02)
```java
// SOURCE: AuthServiceImpl.java:51-58
/**
 * Sem {@code @Transactional} de propósito (SEC-02): o Argon2 é lento de propósito, e uma
 * transação aberta seguraria uma conexão do pool durante todo o hash. ...
 */
@Override
public TokenResponse login(LoginRequest request) {
```

### MODULE_BOUNDARY (API pública)
```java
// SOURCE: srv-luthier-customer/.../package-info.java
@ApplicationModule(displayName = "Clientes", allowedDependencies = "shared")
package com.luthier.customer;
```
Tipos novos usados por outro módulo ficam no **pacote raiz** do módulo (ex.: `com.luthier.customer.CustomerDeactivationGuard`, `com.luthier.product.ProductQueries`).

### MIGRATION_PATTERN
```sql
-- SOURCE: srv-luthier-service/.../db/migration/service/V5_3__service_hardening.sql:1-16
-- Fase 5 (hardening), módulo service.
-- Antes do deploy, cada consulta precisa voltar vazia ou dar 0:
--   SRV-07: select count(*) from service_order_tb where discount < 0 or paid_amount < 0;
alter table service_order_tb
    add constraint ck_order_money check (discount >= 0 and paid_amount >= 0);
```
Cabeçalho com a fase, o módulo e a consulta de pré-checagem. SQL em minúsculas, nomes `ck_`/`uk_`/`fk_`/`idx_`. Precisa rodar em H2 (`MODE=PostgreSQL`) e no PostgreSQL.

### TEST_STRUCTURE (fluxo)
```java
// SOURCE: OrderListQueryCountTest.java:32-70
@SpringBootTest(properties = {
    "app.security.rate-limit.login.capacity=1000",
    "app.security.rate-limit.portal.capacity=1000"})
@AutoConfigureMockMvc
class OrderListQueryCountTest {
  @Autowired MockMvc mvc;
  ApiClient api;
  @BeforeEach void setUp() throws Exception {
    api = new ApiClient(mvc);
    admin = api.registerWorkshop("Oficina Listagem");
    customerId = api.create(admin, "/customers",
        "{\"name\":\"Lucas Silveira\",\"phone\":\"(11) 98765-4321\"}");
```
Contagem de SQL: `spring.jpa.properties.hibernate.generate_statistics=true` + `emf.unwrap(SessionFactory.class).getStatistics()` (`stats.clear()` → chamada → `stats.getPrepareStatementCount()`). Efeito assíncrono: `await().atMost(Duration.ofSeconds(10)).untilAsserted(...)`.

### TEST_STRUCTURE (entidade, sem Spring)
```java
// SOURCE: srv-luthier-service/src/test/.../ServiceOrderEntityTest.java (fase 3)
class ServiceOrderEntityTest {
  private static final String USER = "luthier@teste.com";
  @Test
  void removingAPartBelowTheDiscountIsRejected() {
    var order = order(); service(order, "100"); ...
    assertThatThrownBy(() -> order.removePart(part.getId()))
        .isInstanceOf(ConflictException.class);
```
Os helpers `order()`, `service()`, `part()` e `discount()` já existem nesse arquivo. Use `ReflectionTestUtils` para IDs e status quando preciso.

---

## Branches e versões reservadas

| # | Branch | Achados | Migration |
|---|---|---|---|
| 1 | `fix/app-02-outbox-schema` | APP-02 | `V6_0` (app) |
| 2 | `fix/sec-auth-account` | SEC-06, SEC-07, SEC-08, SEC-19, SEC-20, SEC-12 | — |
| 3 | `fix/sec-03-rate-limit-ipv6` | SEC-03 (+ `RateLimitFilterTest`, parte do SEC-12) | — |
| 4 | `change/service-order-rules` | SRV-04, SRV-05, SRV-06, CUS-03, SRV-10, SRV-11 | `V6_1` (service) |
| 5 | `fix/notification-delivery` | SEC-04, SEC-05, NOT-02 | `V6_2` (notification) |
| 6 | `fix/customer-privacy-search` | CUS-01, CUS-02, CUS-04, PRO-03 | `V6_3` (notification) |
| 7 | `test/sh-06-platform-scope` | SH-06 | — |

**Ordem de merge recomendada:** 1 → 4 → 5 → 6, porque as versões são crescentes e o Flyway recusa versão menor que a última aplicada. As branches 2, 3 e 7 podem entrar em qualquer ordem. Conflitos esperados e triviais:
- 4 e 5 em `ServiceOrderServiceImpl` (regiões diferentes: `requireStockFor`/`nextNumber` × `publishStatus`);
- 4 e 6 em `CustomerServiceImpl` (`deactivate` × `search`/`anonymize`);
- 5 e 6 em `NotificationEntity`/`NotificationRepository`.

---

## Files to Change

| File | Action | Branch | Justification |
|---|---|---|---|
| `srv-luthier-app/src/main/resources/db/migration/app/V6_0__event_publication_official.sql` | CREATE | 1 | APP-02 |
| `srv-luthier-app/src/test/java/com/luthier/OutboxSchemaTest.java` | CREATE | 1 | APP-02 |
| `srv-luthier-shared/.../validation/Cnpj.java`, `ValidDocument.java`, `ValidPhone.java` | CREATE | 2 | SEC-08 |
| `srv-luthier-shared/src/test/.../validation/CnpjTest.java` | CREATE | 2 | SEC-08 |
| `srv-luthier-shared/.../audit/AuditEvent.java` | UPDATE | 2 | SEC-07: campo `actor` |
| `srv-luthier-security/.../domain/records/RegisterTenantRequest.java`, `CreateUserRequest.java` | UPDATE | 2 | SEC-08 |
| `srv-luthier-security/.../domain/repositories/UserRepository.java` | UPDATE | 2 | SEC-06: consulta com lock dos ADMINs |
| `srv-luthier-security/.../services/UserManagementService.java`, `impl/UserManagementServiceImpl.java` | UPDATE | 2 | SEC-06, SEC-20 |
| `srv-luthier-security/.../controllers/UserController.java` | UPDATE | 2 | SEC-06: repassa o `uid` do JWT |
| `srv-luthier-security/.../services/impl/PasswordRecoveryServiceImpl.java` | UPDATE | 2 | SEC-20, SEC-07 |
| `srv-luthier-security/.../services/impl/TenantRegistrationServiceImpl.java` | UPDATE | 2 | SEC-19, SEC-07 |
| `srv-luthier-security/.../services/impl/AuthServiceImpl.java` | UPDATE | 2 | SEC-07 (ator no LOGIN_SUCCESS) |
| `srv-luthier-security/.../services/impl/AuditServiceImpl.java` | UPDATE | 2 | SEC-07 |
| `srv-luthier-app/src/test/java/com/luthier/AuthFlowTest.java` | CREATE | 2 | SEC-12 |
| `srv-luthier-app/src/test/java/com/luthier/UserManagementFlowTest.java` | CREATE | 2 | SEC-12, SEC-06, SEC-08 |
| `srv-luthier-app/src/test/java/com/luthier/AuditActorTest.java` | CREATE | 2 | SEC-07 |
| `srv-luthier-app/src/test/java/com/luthier/PasswordHashPoolTest.java` | CREATE | 2 | SEC-19/20 |
| `srv-luthier-security/.../domain/filters/RateLimitFilter.java` | UPDATE | 3 | SEC-03 |
| `srv-luthier-security/src/test/.../domain/filters/RateLimitFilterTest.java` | CREATE | 3 | SEC-03, SEC-12. O módulo security não tem `src/test` ainda |
| `srv-luthier-security/pom.xml` | UPDATE (se preciso) | 3 | Dependência de teste. O parent já traz `spring-boot-starter-test` (confirmar) |
| `srv-luthier-service/src/main/resources/db/migration/service/V6_1__order_counter_check.sql` | CREATE | 4 | SRV-04 |
| `srv-luthier-service/.../services/impl/OrderCounterInitializer.java` | CREATE | 4 | SRV-04 |
| `srv-luthier-service/.../services/impl/ServiceOrderServiceImpl.java` | UPDATE | 4 (e 5) | SRV-04, SRV-06 (e NOT-02: `eventId`) |
| `srv-luthier-service/.../domain/entities/ServiceOrderEntity.java` | UPDATE | 4 | SRV-05 |
| `srv-luthier-product/.../ProductQueries.java`, `services/impl/ProductQueriesImpl.java` | UPDATE | 4 | SRV-06 |
| `srv-luthier-customer/.../CustomerDeactivationGuard.java` | CREATE | 4 | CUS-03 (SPI pública) |
| `srv-luthier-customer/.../services/impl/CustomerServiceImpl.java` | UPDATE | 4 (e 6) | CUS-03 (e CUS-01/02/04) |
| `srv-luthier-service/.../services/impl/OpenOrdersDeactivationGuard.java` | CREATE | 4 | CUS-03 |
| `srv-luthier-service/.../domain/repositories/ServiceOrderRepository.java` | UPDATE | 4 (e 6) | CUS-03 `existsByCustomerIdAndStatusIn` (e CUS-02 scrub) |
| `srv-luthier-service/.../services/impl/PrivacyServiceImpl.java` | UPDATE (se o teste falhar) | 4 | SRV-10 |
| `srv-luthier-service/src/test/.../ServiceOrderEntityTest.java` | UPDATE | 4 | SRV-05, SRV-11 |
| `srv-luthier-app/src/test/java/com/luthier/ServiceOrderRulesFlowTest.java` | CREATE | 4 | SRV-04/06/10/11, CUS-03 |
| `srv-luthier-notification/src/main/resources/db/migration/notification/V6_2__notification_source_event.sql` | CREATE | 5 | NOT-02 |
| `srv-luthier-service/.../ServiceOrderStatusChanged.java` | UPDATE | 5 | NOT-02: `eventId` |
| `srv-luthier-notification/.../domain/entities/NotificationEntity.java` | UPDATE | 5 | NOT-02: `sourceEventId` |
| `srv-luthier-notification/.../domain/repositories/NotificationRepository.java` | UPDATE | 5 (e 6) | NOT-02 (e CUS-02: `error = null` no scrub) |
| `srv-luthier-notification/.../domain/enums/DeliveryStatus.java` | UPDATE | 5 | Javadoc do `PENDING` |
| `srv-luthier-notification/.../services/impl/Notifier.java` | UPDATE | 5 | NOT-02, CUS-02 (`error` sem destinatário) |
| `srv-luthier-notification/.../listeners/NotificationListeners.java` | UPDATE | 5 | SEC-04, SEC-05, NOT-02 |
| `srv-luthier-notification/.../services/impl/PasswordResetMailer.java` | CREATE | 5 | SEC-04 |
| `srv-luthier-app/src/test/java/com/luthier/NotificationDeliveryTest.java` | CREATE | 5 | SEC-04, SEC-05, NOT-02 |
| `srv-luthier-shared/.../search/LikePatterns.java` | CREATE | 6 | CUS-04, PRO-03 |
| `srv-luthier-shared/src/test/.../search/LikePatternsTest.java` | CREATE | 6 | |
| `srv-luthier-customer/.../domain/repositories/CustomerRepository.java`, `InstrumentRepository.java` | UPDATE | 6 | CUS-01, CUS-04, CUS-02 |
| `srv-luthier-product/.../domain/repositories/ProductRepository.java`, `services/impl/ProductServiceImpl.java` | UPDATE | 6 | PRO-03 |
| `srv-luthier-service/.../listeners/CustomerAnonymizedListener.java` | CREATE | 6 | CUS-02 |
| `srv-luthier-notification/src/main/resources/db/migration/notification/V6_3__notification_customer_backfill.sql` | CREATE | 6 | CUS-02 (legado pré-V4_1) |
| `srv-luthier-app/src/test/java/com/luthier/CustomerPrivacyTest.java` | CREATE | 6 | CUS-01/02/04, PRO-03 |
| `srv-luthier-app/src/test/java/com/luthier/PlatformScopeArchitectureTest.java` | CREATE | 7 | SH-06 |
| `docs/auditoria.md`, `docs/api.md`, `docs/openapi.json`, `docs/security-review.md`, `CLAUDE.md`, `README.md`, `docs/correcoes.prd.md` | UPDATE | cada branch, na parte dela | Regra geral 6 do PRD |

## NOT Building

- **Confirmação de e-mail no cadastro (SEC-05, opção completa).** Depende de decisão de negócio. Entra só a mitigação (e-mail de boas-vindas sem texto do usuário).
- **Índices `pg_trgm` (CUS-04/PRO-03).** Só existem no PostgreSQL e a auditoria os condiciona a "se o volume crescer". Fica registrado em `docs/auditoria.md`.
- **Estorno ao cancelar OS com pagamento.** O SRV-11 só caracteriza o comportamento atual, sem mudar a regra (LOW da fase 3, candidato a achado novo).
- **Reenvio automático de e-mail que falhou (NOT-02).** Continua `falhou` para consulta. Só se elimina a duplicata.
- **Igualar o tempo de resposta do forgot-password (SEC-04).** O envio sai da requisição, mas a diferença de um INSERT continua. A auditoria marca isso como opcional.
- **Guardar a anonimização contra OS aberta.** O CUS-03 trata só a desativação.
- **SH-07 e SEC-21** (BAIXOS novos). Ficam na fase 7.
- **Fases 1/2/3/5: validação no PostgreSQL** (`PostgresMigrationTest` com Docker). Continua pendente nelas. Esta fase também exige o teste, ver Validation.

---

## Step-by-Step Tasks

> Em cada task: **(RED)** escreva o teste, rode e veja falhar pelo motivo certo; **(GREEN)** corrija; rode o teste e o `ModularityTest`. Antes de abrir o PR: `./mvnw -B -ntp verify`.
> Comando de teste isolado: `./mvnw -pl srv-luthier-app -am test -Dtest=<Classe> -Dsurefire.failIfNoSpecifiedTests=false`. Para teste de módulo: `./mvnw -pl <modulo> -am test -Dtest=<Classe> -Dsurefire.failIfNoSpecifiedTests=false`.

### Branch 1: `fix/app-02-outbox-schema`

#### Task 1.1: teste do schema do outbox (RED)
- **ACTION**: criar `OutboxSchemaTest` (`@SpringBootTest`, `JdbcTemplate`).
- **IMPLEMENT**:
  1. `serializedEventAcceptsLargePayload`: insere direto em `event_publication` uma linha com `serialized_event` de 50.000 caracteres (`"x".repeat(50_000)`), `id` = UUID aleatório, `listener_id='t'`, `event_type='t'`, `publication_date=now()`, `completion_attempts=0`, e apaga no fim. Antes da migration falha com 22001.
  2. `outboxHasIndexForPendingLookup`: lê `INFORMATION_SCHEMA.INDEXES` (`TABLE_NAME='EVENT_PUBLICATION'`; siga a consulta de índices que o `SchemaHardeningTest` da fase 5 já usa) e espera `IDX_EVENT_PUBLICATION_LISTENER`.
  3. Opcional (o item 1 já prova o limite): `StockConsumptionRequested` com 300 itens publicado via `ApplicationEventPublisher` dentro de `TransactionTemplate` + `TenantContext.runAs`.
- **MIRROR**: TEST_STRUCTURE (fluxo); a consulta de metadados do `SchemaHardeningTest` (fase 5).
- **GOTCHA**: `JdbcTemplate` grava sem tenant. A tabela `event_publication` não tem `tenant_id`, então tudo bem.
- **VALIDATE**: falha com "Value too long" antes da V6_0.

#### Task 1.2: migration `V6_0__event_publication_official.sql`
- **ACTION**: criar em `srv-luthier-app/src/main/resources/db/migration/app/`.
- **IMPLEMENT**:
```sql
-- Fase 6, módulo app (APP-02). Alinha o outbox com o mapeamento JPA do Spring Modulith 2.1.1
-- (JpaEventPublication: Strings sem tamanho) e com as consultas que ele faz.
-- Pré-checagem: nenhuma (só alarga colunas e cria índice).
alter table event_publication alter column serialized_event set data type varchar;
alter table event_publication alter column listener_id set data type varchar;
alter table event_publication alter column event_type set data type varchar;
-- Pendentes por ouvinte (markCompleted por evento + listener; findIncompletePublications)
create index idx_event_publication_listener on event_publication (listener_id, completion_date);
-- Republicação ordena os pendentes por data
create index idx_event_publication_pending on event_publication (completion_date, publication_date);
drop index idx_event_publication_completion;
```
- **GOTCHA**:
  - **Não use `text`** (vira CLOB no H2 e quebra o `validate`).
  - Um índice B-tree em `serialized_event` estoura o limite de ~2,7 KB do PostgreSQL. O schema oficial usa `using hash`, que só existe no PostgreSQL, por isso fica de fora. Registre isso em `docs/auditoria.md`.
  - Antes de remover `idx_event_publication_completion`, confirme que nenhuma outra migration o referencia.
- **VALIDATE**: `OutboxSchemaTest` verde, suíte inteira verde (o Hibernate valida as colunas do outbox).

#### Task 1.3: documentação
- `docs/auditoria.md`: ✔ no APP-02, com a nota do índice hash. `CLAUDE.md`: incluir `V6_0`… na lista de versões. O ⚠ do APP-02 exige o **`PostgresMigrationTest` com Docker** antes do merge.

### Branch 2: `fix/sec-auth-account`

#### Task 2.1: SEC-08, validadores (RED → GREEN)
- **ACTION**: criar em `com.luthier.shared.validation`:
  - `Cnpj` (`digits`, `isValid` com os dois dígitos verificadores, pesos `5,4,3,2,9,8,7,6,5,4,3,2` e `6,5,4,3,2,9,8,7,6,5,4,3,2`, e rejeição de dígitos todos iguais);
  - `@ValidDocument`: aceita null ou vazio, ou dígitos normalizados com tamanho 11 e `Cpf.isValid`, ou tamanho 14 e `Cnpj.isValid`;
  - `@ValidPhone`: aceita null ou vazio, ou 10 ou 11 dígitos depois de `Phone.digits`.
- **MIRROR**: `ValidCpf.java` (anotação + `Validator` interno), `CpfTest`.
- **IMPLEMENT**: `RegisterTenantRequest.Workshop.document` troca o `@Pattern` por `@ValidDocument`. `Owner.phone` e `CreateUserRequest.phone` passam a `@ValidPhone` (mantenha o `@Size(max = 20)` para o texto bruto).
- **TEST**:
  - `CnpjTest` (válido `11.222.333/0001-81`; DV errado; todos iguais);
  - em `UserManagementFlowTest`: `POST /auth/register` com `document:"123456789012345"` dá **400** (antes, 500) e com `phone:"+55 11 98765-4321"` (13 dígitos) dá 400.
- **GOTCHA**: o `ApiClient.registerWorkshop` não envia document nem phone, então continua funcionando.
- **VALIDATE**: os dois casos dão 400 com `application/problem+json`.

#### Task 2.2: SEC-06, nunca sem ADMIN (RED → GREEN)
- **ACTION**: proteger `changeProfile` e `deactivate`.
- **IMPLEMENT**:
  - `UserRepository`:
    ```java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.tenantId = :tenantId and u.profile = :profile and u.active = true")
    List<UserEntity> lockActiveByTenantAndProfile(@Param("tenantId") UUID tenantId, @Param("profile") Profile profile);
    ```
  - `UserManagementService`: assinaturas `changeProfile(UUID id, Profile profile, UUID actorId)` e `deactivate(UUID id, UUID actorId)`.
  - `UserController`: recebe `@AuthenticationPrincipal Jwt jwt` e passa `UUID.fromString(jwt.getClaimAsString("uid"))`, como o `me` já faz.
  - `UserManagementServiceImpl`, método privado:
    ```java
    /** SEC-06: a luthieria nunca fica sem ADMIN ativo (não há recuperação: AdminBootstrap só roda sem nenhum ADMIN na plataforma). */
    private void requireAnotherActiveAdmin(UserEntity target, UUID actorId) {
      if (target.getUuid().equals(actorId)) {
        throw new ConflictException("Você não pode rebaixar nem desativar a própria conta");
      }
      if (target.getProfile() != Profile.ADMIN || !target.isActive()) return;
      var admins = userRepository.lockActiveByTenantAndProfile(target.getTenantId(), Profile.ADMIN);
      if (admins.size() <= 1) {
        throw new ConflictException("A luthieria precisa de pelo menos um ADMIN ativo");
      }
    }
    ```
    Chame em `deactivate` sempre e em `changeProfile` só quando `profile != Profile.ADMIN`.
- **GOTCHA**: o lock evita que dois ADMINs se desativem em paralelo e zerem o tenant. O `find(id)` acontece antes do lock: carregue o alvo, trave a lista e só então decida.
- **TEST** (`UserManagementFlowTest`):
  - ADMIN rebaixa a si mesmo → 409;
  - ADMIN desativa a si mesmo → 409;
  - com 2 ADMINs, A desativa B → 200, e depois tentar o mesmo com o último → 409;
  - LUTHIER desativado por ADMIN → 200.
- **VALIDATE**: os cenários 409 falham antes (respondem 200).

#### Task 2.3: SEC-19 + SEC-20, Argon2 fora da transação (RED → GREEN)
- **ACTION**: tirar `passwordEncoder.encode` de dentro de transação em `TenantRegistrationServiceImpl.register`, `UserManagementServiceImpl.create` e `PasswordRecoveryServiceImpl.reset`.
- **IMPLEMENT**:
  - `register`: remova o `@Transactional` do método público. Calcule `var hash = passwordEncoder.encode(owner.password())` primeiro, depois chame um bean interno `@Transactional` (padrão `Consumer`/`Purger`: `@Component static class Writer` no mesmo arquivo, package-private) que faz as checagens, os `saveAndFlush`, os eventos e o `authService.startSession(user)`. A ordem do SH-02 continua: `saveAndFlush` antes do `AuditEvent`.
  - `create`: a classe tem `@Transactional` (jakarta) no nível da classe. Extraia um bean interno `Writer`, como acima: o hash é calculado antes, sem transação, e o `Writer` revalida o e-mail e o CPF e grava. O método `create` não pode ficar sob o `@Transactional` da classe (use `@Transactional(Transactional.TxType.NOT_SUPPORTED)` nele, ou mova a anotação da classe para os demais métodos).
  - `reset`: sem transação no método público.
    1. Leitura curta (`@Transactional(readOnly = true)` no `Writer`): o token existe, não foi usado e não expirou; senão `InvalidTokenException.reset()`. Isso evita gastar Argon2 com token inválido.
    2. `encode`.
    3. `Writer.apply(tokenHash, hash)` `@Transactional`: `findForUpdateByTokenHash`, **revalida** (uso único sob lock), `changePassword`, `markUsed`, `revokeAllByUser` e o `AuditEvent`.
  - Javadoc no método público explicando o SEC-02, igual ao de `AuthServiceImpl.login`.
- **MIRROR**: SERVICE_PATTERN; o bean interno de `StockConsumptionListener.Consumer`.
- **GOTCHA**:
  - `PasswordRecoveryServiceImpl` e `UserManagementServiceImpl` usam `jakarta.transaction.Transactional`. Nas classes `Writer` use o `org.springframework.transaction.annotation.Transactional`, que é o padrão dos outros arquivos; o `readOnly` só existe nele.
  - O `authService.startSession` é `@Transactional` e pode rodar dentro do `Writer`.
- **TEST** (`PasswordHashPoolTest`): espelhe `LoginConnectionPoolTest`, com pool de 2 conexões (mesmas propriedades daquele teste). Dispare 3 `register` simultâneos e 3 `reset-password` simultâneos (tokens obtidos com `@RecordApplicationEvents` + `ApplicationEvents.stream(PasswordResetRequested.class)`). Todos terminam com 201/204 dentro de `hikari.connection-timeout` + folga, sem `SQLTransientConnectionException`.
- **VALIDATE**: o teste falha (timeout/500) antes e passa depois. `RegistrationFlowTest` continua verde.

#### Task 2.4: SEC-07, ator da auditoria (RED → GREEN)
- **ACTION**: `AuditEvent` ganha o ator explícito. `AuditServiceImpl` passa a ignorar o anônimo e a gravar o UUID.
- **IMPLEMENT**:
  - `AuditEvent(UUID tenantId, String action, String resourceType, String resourceId, String details, String actor)` passa a ser o canônico. Mantenha um construtor de 5 argumentos que delega com `actor = null`, para nenhum chamador quebrar. O `of(...)` continua igual.
  - `AuditServiceImpl.record`: `var actor = event.actor() != null ? event.actor() : currentActor();`
  - `currentActor()`:
    ```java
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth instanceof AnonymousAuthenticationToken) return ANONYMOUS; // "anônimo"
    if (auth.getPrincipal() instanceof Jwt jwt && jwt.getClaimAsString("uid") != null) return jwt.getClaimAsString("uid");
    return ANONYMOUS;
    ```
  - Ator explícito (UUID do usuário) em `LOGIN_SUCCESS` (`AuthServiceImpl`), `PASSWORD_RESET` (`PasswordRecoveryServiceImpl`) e `TENANT_REGISTERED` (`TenantRegistrationServiceImpl`, UUID do dono). `LOGIN_FAILED` fica `anônimo` (já leva o e-mail mascarado em `details`).
- **GOTCHA**:
  - O módulo security já depende do resource server (`Jwt`; o subject do JWT é o e-mail e o `uid` é o UUID, ver `TokenServiceImpl:36-46`).
  - `shared` não pode importar Spring Security. A lógica do ator fica no `AuditServiceImpl` (security), e `AuditEvent` só carrega uma `String`.
- **TEST** (`AuditActorTest`): faça login, registro, reset e uma ação autenticada (criar usuário). Leia `GET /audit-logs` com o ADMIN e confira que nenhum `actor` é `anonymousUser` nem contém `@`, e que o do `USER_CREATED` é o `uid` do ADMIN.
- **VALIDATE**: falha antes (`anonymousUser` e e-mail). `docs/security-review.md`: troque "e-mails mascarados" por "ator registrado pelo UUID do usuário".

#### Task 2.5: SEC-12, testes dos fluxos de autenticação
- **ACTION**: criar `AuthFlowTest` e completar `UserManagementFlowTest`. São testes de cobertura e passam no código atual, exceto onde as tasks acima mudam o comportamento.
- **IMPLEMENT** (`AuthFlowTest`, rate limit 1000):
  - refresh rotaciona: o token novo funciona e o antigo dá 401;
  - reuso do antigo revoga a família: o novo também passa a dar 401;
  - logout revoga, e o refresh depois dá 401;
  - o logout de um token desconhecido dá 204 (idempotente);
  - forgot + reset: o token é capturado com `@RecordApplicationEvents` + `ApplicationEvents.stream(PasswordResetRequested.class)` (o `toString` mascara, mas o record tem o token bruto). O reset troca a senha; o login com a senha nova dá 200 e com a antiga dá 401; o reuso do token dá 400; o refresh anterior ao reset dá 401;
  - token expirado: atualize `expires_at` para o passado via `JdbcTemplate` e espere 400;
  - login de usuário desativado dá 401; tenant desativado (`update tenant_tb set active=false` via `JdbcTemplate`) dá 401 no refresh.
- **IMPLEMENT** (`UserManagementFlowTest`):
  - ADMIN da luthieria A faz `PATCH`/`deactivate` em usuário da B → 404;
  - desativar ou mudar o perfil revoga o refresh do usuário (o refresh dele dá 401);
  - mais os casos de SEC-06 e SEC-08.
- **GOTCHA**: confira os status reais de logout e reset no `AuthController` (204 ou 200) antes de fixar as asserções.
- **VALIDATE**: verde. Os casos de SEC-06/SEC-08 eram vermelhos antes da task correspondente.

#### Task 2.6: docs da branch
- `docs/api.md`: na linha de `/users`, documentar o 409 (própria conta e último ADMIN). `docs/openapi.json`: respostas 409 nos dois endpoints e 400 no register. `docs/auditoria.md`: ✔ em SEC-06/07/08/12; registrar **SEC-19** e **SEC-20** (IDs novos) como resolvidos. PRD: mover SEC-19 e SEC-20 de "Achados surgidos" para a fase 6.

### Branch 3: `fix/sec-03-rate-limit-ipv6`

#### Task 3.1: chave por /64 (RED → GREEN)
- **ACTION**: `RateLimitFilter` passa a usar `clientKey(request.getRemoteAddr())`.
- **IMPLEMENT** (método `static` package-private, testável):
  ```java
  /** SEC-03: IPv6 por prefixo /64 (um cliente controla o /64 inteiro); IPv4 e IPv4-mapeado completos. */
  static String clientKey(String remoteAddr) {
    if (remoteAddr == null || remoteAddr.indexOf(':') < 0) return remoteAddr;
    try {
      var address = InetAddress.getByName(remoteAddr.split("%")[0]); // literal: sem DNS
      if (address instanceof Inet6Address) {
        var prefix = Arrays.copyOf(address.getAddress(), 8);
        return HexFormat.of().formatHex(prefix) + "/64";
      }
      return address.getHostAddress(); // ::ffff:a.b.c.d vira Inet4Address
    } catch (UnknownHostException e) {
      return remoteAddr;
    }
  }
  ```
  A chave passa a ser `route.name() + "|" + clientKey(request.getRemoteAddr())`.
- **GOTCHA**:
  - `InetAddress.getByName` com um **literal** IPv6/IPv4 não consulta DNS. Com `forward-headers-strategy: native`, o `getRemoteAddr()` é sempre um literal.
  - O módulo security **não tem `src/test`**. Crie `srv-luthier-security/src/test/java/com/luthier/security/domain/filters/RateLimitFilterTest.java` (mesmo pacote, para acessar o package-private). Confirme no `srv-luthier-parent/pom.xml` que `spring-boot-starter-test` é herdado com escopo `test`; se for, não precisa mexer no pom.
- **TEST** (`RateLimitFilterTest`, unitário, `MockHttpServletRequest`/`MockHttpServletResponse`/`MockFilterChain`):
  - `RateLimitFilter.auth(2, Duration.ofMinutes(1))`: duas requisições de `2001:db8:1:2::1` e `2001:db8:1:2::ffff` passam e a terceira, de `2001:db8:1:2:aaaa::1`, dá **429** (antes passava);
  - um prefixo diferente (`2001:db8:1:3::1`) não é afetado;
  - IPv4 continua por endereço;
  - casos do SEC-01: POST `/auth/%6cogin` (com `setRequestURI` e `setServletPath("/auth/login")`) consome o balde de `/auth/login`; GET em `/auth/login` não é limitado.
- **VALIDATE**: o cenário /64 é vermelho antes.

#### Task 3.2: docs
- `README.md` (seção de deploy) e `docs/security-review.md`: o proxy de borda **precisa sobrescrever** `X-Forwarded-For`, e a chave IPv6 é por /64. `docs/auditoria.md`: ✔ SEC-03.

### Branch 4: `change/service-order-rules`

#### Task 4.1: SRV-05, encerrar o orçamento (RED → GREEN)
- **ACTION**: em `ServiceOrderEntity.changeStatus`, quando `status == AGUARDANDO_APROVACAO` e a transição for válida (`→ ORCAMENTO` ou `→ CANCELADO`), feche o orçamento.
- **IMPLEMENT**:
  ```java
  var leavingPendingQuote = status == OrderStatus.AGUARDANDO_APROVACAO && quoteStatus == QuoteStatus.PENDENTE;
  var previous = transition(target);
  if (leavingPendingQuote) {
    closePendingQuote(note, user); // SRV-05 (decisão A, docs/correcoes.prd.md)
  }
  ```
  ```java
  /** Orçamento sem resposta do cliente não fica "pendente" para sempre (SRV-05). */
  private void closePendingQuote(String note, String user) {
    quoteStatus = QuoteStatus.REJEITADO;
    quoteAnsweredAt = Instant.now();
    rejectionReason = note == null || note.isBlank() ? "Encerrado pela oficina" : note;
    log(TimelineType.QUOTE_REJECTED, "Orçamento encerrado pela oficina", rejectionReason, user);
  }
  ```
  Chame **depois** do `transition`, para a exceção de transição inválida não alterar nada.
- **TEST** (`ServiceOrderEntityTest`, helpers existentes):
  - OS com serviço, `sendQuote`, `changeStatus(ORCAMENTO, "cliente não respondeu", USER)`: `quoteStatus == REJEITADO`, `quoteAnsweredAt != null`, `rejectionReason == "cliente não respondeu"`, e a timeline contém `QUOTE_REJECTED`;
  - o mesmo para `CANCELADO` sem nota: motivo "Encerrado pela oficina";
  - `changeStatus` fora de `AGUARDANDO_APROVACAO` não mexe em `quoteStatus`;
  - depois do SRV-05, `sendQuote` de novo volta a `PENDENTE`.
- **GOTCHA**: o `rejectionReason` tem `length = 1000`. A `note` vem de `ChangeStatus`; confira o `@Size` dela em `OrderRequests` (se faltar, adicione `@Size(max = 1000)`).
- **VALIDATE**: falha antes (`quoteStatus` continua `PENDENTE`).

#### Task 4.2: SRV-06, baixa de peça inativa (RED → GREEN)
- **ACTION**: `ProductQueries` ganha `Optional<ProductSnapshot> findIncludingInactive(UUID productId)`, com javadoc: "Inclui produto desativado: só para conferir peças que já estão numa OS (SRV-06)". Use em `requireStockFor`.
- **IMPLEMENT**: `ProductQueriesImpl.findIncludingInactive` → `productRepository.findById(productId).map(...)`, com o mesmo mapeamento de `find` (extraia `toSnapshot`). Em `ServiceOrderServiceImpl.requireStockFor`, troque `products.find` por `products.findIncludingInactive`. O `addPart` **continua** com `find` (produto inativo não entra em OS nova).
- **TEST** (`ServiceOrderRulesFlowTest`, ponta a ponta):
  1. OS com peça (quantidade 2) e saldo 10;
  2. orçamento enviado e aprovado;
  3. `DELETE /products/{id}`;
  4. `status pronto` dá 200;
  5. `await` até o saldo do produto ficar 8. Leia por `JdbcTemplate`, porque o `GET /products/{id}` filtra inativos.
- **GOTCHA**: o `StockConsumptionListener.Consumer` já usa `findById` (sem filtro `active`), então a baixa assíncrona funciona sem mudança. Confira no arquivo pós-fase 3.
- **VALIDATE**: falha antes (409 "Peça não está mais no estoque").

#### Task 4.3: CUS-03, 409 ao desativar cliente com OS aberta (RED → GREEN)
- **ACTION**: SPI no customer, implementação no service.
- **IMPLEMENT**:
  - `com.luthier.customer.CustomerDeactivationGuard` (pacote raiz, API pública):
    ```java
    /**
     * Ponto de extensão para outros módulos vetarem a desativação de um cliente (CUS-03).
     * Implementado por quem depende de customer (ex.: service), sem criar dependência de volta.
     * Lança ConflictException quando o cliente não pode ser desativado.
     */
    public interface CustomerDeactivationGuard {
      void assertCanDeactivate(UUID customerId);
    }
    ```
  - `CustomerServiceImpl`: recebe `List<CustomerDeactivationGuard> deactivationGuards` no construtor. Em `deactivate`: `var customer = find(id); deactivationGuards.forEach(g -> g.assertCanDeactivate(id)); customer.deactivate(); ...`
  - `com.luthier.service.services.impl.OpenOrdersDeactivationGuard` (`@Component`, implementa a SPI): `if (orders.existsByCustomerIdAndStatusIn(customerId, OPEN)) throw new ConflictException("Cliente tem OS em aberto; conclua ou cancele antes de desativar");`, com `OPEN = Arrays.stream(OrderStatus.values()).filter(OrderStatus::isOpen).toList()`, igual ao `ReportServiceImpl:63`.
  - `ServiceOrderRepository`: `boolean existsByCustomerIdAndStatusIn(UUID customerId, Collection<OrderStatus> statuses);`
- **GOTCHA**:
  - O `ModularityTest` só olha tipos. O customer referencia só o próprio tipo, e o service referencia `com.luthier.customer.CustomerDeactivationGuard`, que é permitido. **Não** acrescente `service` aos `allowedDependencies` do customer.
  - Sem implementações, a lista vem vazia, o que é aceito.
  - O `ConflictException` vem do shared.
- **TEST** (`ServiceOrderRulesFlowTest`):
  - cliente com OS `recebido`: `DELETE /customers/{id}` → 409 e o cliente continua no `GET`;
  - depois de cancelar a OS → 204;
  - cliente sem OS → 204.
- **VALIDATE**: falha antes (204). `docs/api.md`: 409 no DELETE de cliente.

#### Task 4.4: SRV-04, primeira OS concorrente (RED → GREEN)
- **ACTION**: criar o contador numa transação própria, tolerando corrida, e o CHECK `last_number >= 0`.
- **IMPLEMENT**:
  - `OrderCounterInitializer` (`@Component` package-private em `services/impl`):
    ```java
    /** SRV-04: cria o contador da luthieria em transação própria; se outra abertura criou antes, ignora. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists() {
      if (repository.count() == 0) repository.saveAndFlush(OrderCounterEntity.start());
    }
    ```
  - `ServiceOrderServiceImpl.nextNumber()`:
    ```java
    var counter = counterRepository.lockCurrent();
    if (counter.isEmpty()) {
      try { counterInitializer.ensureExists(); }
      catch (DataIntegrityViolationException raced) { /* outra abertura criou: o lock abaixo serializa */ }
      counter = counterRepository.lockCurrent();
    }
    return counter.orElseThrow().next();
    ```
  - `V6_1__order_counter_check.sql`:
    ```sql
    -- Fase 6, módulo service (SRV-04).
    -- Antes do deploy: select count(*) from service_order_counter_tb where last_number < 0; -- 0
    alter table service_order_counter_tb add constraint ck_order_counter_last_number check (last_number >= 0);
    ```
- **GOTCHA**:
  - O `REQUIRES_NEW` pede uma segunda conexão, mas só na primeira OS de cada luthieria. Registre isso no javadoc (risco do SEC-02 aceitável aqui).
  - O `TenantContext` da requisição continua valendo na transação nova (mesma thread), e o `@TenantId` preenche o tenant.
  - Confirme o nome real da tabela/coluna (`SERVICE_ORDER_COUNTER_TB`, `last_number`) e se já existe o `uk_order_counter_tenant` em `V2_0`.
- **TEST** (`ServiceOrderRulesFlowTest.firstOrdersOfAWorkshopInParallelGetDistinctNumbers`): luthieria nova, cliente e instrumento; 4 `POST /service-orders` em paralelo (`ExecutorService` + `CountDownLatch`, cada um com o mesmo token). Todos dão 201 e os números são `{1,2,3,4}`. No H2 a corrida não é determinística: rode o cenário 5 vezes num loop com luthierias novas. Antes da correção, falha de forma intermitente (500/409). Aceite que o vermelho é probabilístico e registre isso no relatório. Mais um caso: `update service_order_counter_tb set last_number = -1` via `JdbcTemplate` é rejeitado.
- **VALIDATE**: verde em 5 de 5 execuções.

#### Task 4.5: SRV-10, exportação sem N+1 (caracterização → correção se preciso)
- **ACTION**: teste de contagem de SQL em `GET /privacy/customers/{id}/export`.
- **IMPLEMENT**:
  - Teste: cliente com 3 OS, `stats.clear()`, exportação, contagem `c3`; depois mais 9 OS (12 no total), contagem `c12`. Espere `c12 == c3`. Com o `default_batch_fetch_size: 50` do APP-04, as 4 coleções vêm em lotes, então o teste **deve passar já**. Nesse caso o SRV-10 fica resolvido pelo APP-04 com prova.
  - Se falhar: em `ServiceOrderRepository`, `@EntityGraph(attributePaths = "services")` em `findAllByCustomerIdOrderByOrderNumberDesc` (uma coleção por vez, para evitar `MultipleBagFetchException`). As outras três ficam com o batch.
  - "Sem limite": a exportação LGPD precisa ser completa, então **não paginar**. Registre no javadoc de `PrivacyServiceImpl`.
- **VALIDATE**: `c12 == c3`. ✔ no SRV-10, com a nota "mitigado pelo APP-04, comprovado por teste".

#### Task 4.6: SRV-11, lacunas de teste
- **ACTION**: completar `ServiceOrderEntityTest` (unitário) e `ServiceOrderRulesFlowTest` (integração). Antes de escrever, verifique o que a fase 3 já cobriu (SRV-01 e reentrega do PRO-01) e não duplique.
- **IMPLEMENT**:

  | Caso | Onde | Esperado |
  |---|---|---|
  | Cancelamento com pagamento | entidade | `EM_EXECUCAO` + pagamento → `changeStatus(CANCELADO)` é aceito e `paidAmount` continua igual (caracterização, sem estorno; ver NOT Building) |
  | Conflito de `@Version` | fluxo | Num `TransactionTemplate`, carregue a OS, faça `update service_order_tb set version = version + 1` via `JdbcTemplate` e altere a entidade → `ObjectOptimisticLockingFailureException`; pela API, o handler do SH-02 responde **409** |
  | Baixa assíncrona por saldo insuficiente | fluxo | Publique um `StockConsumptionRequested` com quantidade maior que o saldo, dentro de `TenantContext.runAs` + `TransactionTemplate`. O `event_publication` fica com uma linha com `completion_date is null` e o saldo não muda |
  | Recusa do orçamento pelo portal | fluxo | `POST /portal/orders/{token}/quote/reject` com motivo: status `orcamento`, `quoteStatus` `rejeitado` |
  | `AJUSTE` não deixa negativo | fluxo | `POST /products/{id}/movements` com `type: ajuste`, `quantity: -1` → 400 (confira a validação em `StockMovementRequest`; o CHECK do PRO-04 garante no banco) |
  | Numeração concorrente | Task 4.4 | — |
  | Falha no envio de e-mail | Branch 5 (`NotificationDeliveryTest`) | — |
  | `NotificationRetentionJob` | já coberto por `RetentionJobFlowTest` (fase 2) | só referenciar |
- **VALIDATE**: todos verdes. São de caracterização, e o relatório indica quais.

#### Task 4.7: docs
- `docs/api.md`, seção "Ciclo da OS": "sair de `aguardando_aprovacao` por `/status` encerra o orçamento como `rejeitado`" e "`pronto` aceita peça desativada depois de entrar na OS". `docs/openapi.json`: 409 no `DELETE /customers/{id}`. `docs/auditoria.md`: ✔ SRV-04/05/06/10/11 e CUS-03. `CLAUDE.md`: versões e o padrão da SPI `CustomerDeactivationGuard` em "Modules and boundaries".

### Branch 5: `fix/notification-delivery`

#### Task 5.1: SEC-05, boas-vindas sem texto do usuário (RED → GREEN)
- **ACTION**: `Handlers.welcome` monta o corpo só com texto fixo e o `frontendUrl`.
- **IMPLEMENT**:
  ```java
  notifier.email(e.ownerEmail(), "Bem-vindo(a) à Luthieria!", """
      Olá,

      Sua oficina foi cadastrada com sucesso. Acesse %s para cadastrar clientes,
      instrumentos, estoque e abrir a primeira ordem de serviço.

      Se não foi você quem fez este cadastro, ignore este e-mail.
      """.formatted(frontendUrl), "tenant", e.tenantId(), null);
  ```
- **TEST** (`NotificationDeliveryTest`): cadastro com `workshop.name = "Clique em http://phish.example"` e `owner.name = "<script>"`. `await` até existir a notificação `tenant` (`GET /notifications?channel=email` com o token do dono) e confira que o `body` não contém `phish.example` nem `<script>`.
- **VALIDATE**: falha antes.

#### Task 5.2: SEC-04, forgot-password fora da requisição (RED → GREEN)
- **ACTION**: o envio sai da thread da requisição e da transação, sem outbox.
- **IMPLEMENT**:
  - `PasswordResetMailer` (`@Component` em `notification/services/impl`):
    ```java
    /**
     * SEC-04: o e-mail de redefinição sai DEPOIS do commit, fora da requisição, para que a latência
     * do /auth/forgot-password não revele se o e-mail existe e o SMTP não segure conexão do pool.
     * O evento carrega o token bruto: não pode passar pelo outbox (event_publication), por isso o
     * listener continua @EventListener síncrono e só agenda o envio.
     * Executor próprio como CAMPO, não bean: um bean Executor desligaria o applicationTaskExecutor
     * do Boot usado pelo @Async.
     */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    public void sendAfterCommit(Runnable send) {
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
          @Override public void afterCommit() { executor.execute(send); }
        });
      } else {
        executor.execute(send);
      }
    }
    @PreDestroy void shutdown() { executor.shutdown(); }
    ```
  - `NotificationListeners.on(PasswordResetRequested)`: continua `@EventListener`. Monte o corpo como hoje e chame `mailer.sendAfterCommit(() -> { try { emailSender.send(...) } catch (RuntimeException ex) { log.warn("Falha ao enviar e-mail de redefinição de senha: {}", ex.getClass().getSimpleName()); } })`.
- **GOTCHA**:
  - `PasswordRecoveryServiceImpl.requestReset` é `@Transactional` (jakarta). O Spring sincroniza também com a anotação JTA, então o `afterCommit` dispara depois do commit. O teste confirma.
  - **Não** troque para `@TransactionalEventListener`: o Modulith gravaria o evento, com o token bruto, no `event_publication`.
  - A Task 2.3 (branch 2) mexe no `reset`, não no `requestReset`, então não há conflito.
- **TEST**: `@MockitoSpyBean EmailSender emailSender`, com `doAnswer(inv -> { Thread.sleep(1500); return true; }).when(emailSender).send(anyString(), eq("Redefinição de senha"), anyString())`. Cadastre o usuário e meça `POST /auth/forgot-password`: a resposta 202 chega em **< 500 ms** (antes, ≥ 1500). Depois, `verify(emailSender, timeout(5000)).send(eq(email), eq("Redefinição de senha"), contains("#token="))`.
- **VALIDATE**: falha antes pelo tempo.

#### Task 5.3: NOT-02, e-mail depois do commit e idempotente (RED → GREEN)
- **ACTION**: grave a notificação como `pendente` na transação. O envio acontece depois do commit e o resultado é gravado em transação curta. A reentrega do outbox não duplica.
- **IMPLEMENT**:
  - `ServiceOrderStatusChanged` ganha `UUID eventId` (último componente). `ServiceOrderServiceImpl.publishStatus` passa `UUID.randomUUID()`. O id fica serializado no outbox, então a reentrega traz o mesmo valor.
  - `V6_2__notification_source_event.sql`:
    ```sql
    -- Fase 6, módulo notification (NOT-02): uma notificação por evento e canal, mesmo com reentrega do outbox.
    -- Pré-checagem: nenhuma (coluna nova, nula nas linhas existentes; nulos não conflitam no índice único).
    alter table notification_tb add column source_event_id uuid;
    create unique index uk_notification_source_event on notification_tb (tenant_id, source_event_id, channel);
    ```
  - `NotificationEntity`: campo `sourceEventId` (`@Column(name = "source_event_id")`), método `fromEvent(UUID)` no estilo de `forCustomer`, e `@Index`/`uniqueConstraints` espelhando a migration.
  - `NotificationRepository`:
    - `boolean existsBySourceEventId(UUID id)`;
    - `List<NotificationEntity> findAllBySourceEventIdAndChannelAndStatus(UUID id, Channel c, DeliveryStatus s)`.
  - `Notifier`:
    - `email(...)` deixa de enviar: só grava `PENDING` com `fromEvent(eventId)` e devolve a entidade;
    - o envio não pode ficar dentro da transação. Dois métodos de bean: `@Transactional(readOnly = true) List<PendingEmail> pendingEmails(UUID sourceEventId)` (record com id, to, subject e body) e `@Transactional recordResult(UUID id, boolean sent, String error)`, que carrega a entidade e chama `markSent`/`markFailed`;
    - `markFailed` grava só a classe da exceção (`e.getClass().getSimpleName()`), nunca `getMessage()`, que pode trazer o destinatário (CUS-02);
    - o contador `luthieria.notifications` vai para o `recordResult` no caso do e-mail.
  - `NotificationListeners.on(ServiceOrderStatusChanged)`:
    ```java
    TenantContext.runAs(event.tenantId(), () -> {
      handlers.orderStatusChanged(event);          // tx: cria e-mail PENDING + WhatsApp, se ainda não existem para o eventId
      deliverPendingEmails(event.eventId());        // fora de tx: envia e grava o resultado em tx curta
    });
    ```
    `deliverPendingEmails`: para cada `PendingEmail`, `emailSender.send(...)` em try/catch e depois `notifier.recordResult(...)`.
  - `Handlers.orderStatusChanged`: no início, `if (e.eventId() != null && repository.existsBySourceEventId(e.eventId())) return;`.
  - Mesmo tratamento no `welcome`: o `sourceEventId` é o `tenantId` (cadastro único por luthieria), e o envio acontece depois do commit da mesma forma.
- **GOTCHA**:
  - **Compatibilidade:** eventos já no outbox no momento do deploy não têm `eventId` (Jackson desserializa `null`). Com `null`, não faça a checagem de existência e não grave `source_event_id`. É o comportamento antigo, só nessa transição.
  - A janela "enviou e caiu antes de gravar `SENT`" continua (at-least-once). A reentrega encontra o e-mail ainda `PENDING` e reenvia. Documente no javadoc.
  - O javadoc de `DeliveryStatus.PENDING` diz "Aguardando alguém da oficina enviar (WhatsApp) ou ler (alerta interno)". Inclua "ou e-mail na fila de envio".
  - O `NotificationFlowTest` pode conferir `status=enviado` logo depois do evento. Use `await` se quebrar.
- **TEST** (`NotificationDeliveryTest`):
  1. **Reentrega não duplica:** chame `listeners.on(event)` duas vezes com o mesmo `ServiceOrderStatusChanged` (mesmo `eventId`; chamada direta no bean é síncrona). Com `@MockitoSpyBean EmailSender`, `verify(emailSender, times(1)).send(...)`, e existe 1 notificação de e-mail para o `relatedId`. Falha antes (2).
  2. **Falha de SMTP não desfaz o WhatsApp e grava `falhou` sem destinatário no `error`:** `doThrow(new MailSendException("550 user lucas@x.com unknown"))`. A notificação de e-mail fica `falhou`, o `error` não contém `@`, e a de WhatsApp existe.
  3. Opcional: SMTP lento (sleep 1 s) não segura conexão. O teste 1 já cobre a regressão principal.
- **VALIDATE**: os testes 1 e 2 falham antes.

#### Task 5.4: docs
- `docs/auditoria.md`: ✔ SEC-04/05 e NOT-02; registre no SEC-05 que a confirmação de e-mail continua pendente de decisão. `docs/security-review.md`: anti-enumeração e boas-vindas. `CLAUDE.md`: na seção de eventos, "`ServiceOrderStatusChanged` carrega `eventId` (idempotência das notificações)" e as versões.

### Branch 6: `fix/customer-privacy-search`

#### Task 6.1: utilitário de LIKE (RED → GREEN)
- **ACTION**: `com.luthier.shared.search.LikePatterns` (o shared é kernel aberto; confira em `shared/package-info.java` se o subpacote precisa ser declarado. Se precisar, siga o padrão de `validation`).
- **IMPLEMENT**:
  ```java
  /** CUS-04/PRO-03: valor do usuário vira literal no LIKE (use com "escape '\\'"). */
  public static String contains(String raw) {
    var escaped = raw.toLowerCase(Locale.ROOT)
        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }
  ```
- **TEST**: `LikePatternsTest`: `"50%"` → `"%50\\%%"`, `"a_b"` → `"%a\\_b%"`, `"c:\\x"` escapado, minúsculas.

#### Task 6.2: CUS-01 + CUS-04, busca de clientes (RED → GREEN)
- **IMPLEMENT**:
  ```java
  @Query("""
      select c from CustomerEntity c
      where c.active = true
        and (lower(c.name) like :pattern escape '\\'
             or (:phoneDigits is not null and c.phone like :phoneDigits)
             or (:cpf is not null and c.cpf = :cpf))
      """)
  Page<CustomerEntity> search(@Param("pattern") String pattern, @Param("phoneDigits") String phoneDigits,
      @Param("cpf") String cpf, Pageable pageable);
  ```
  `CustomerServiceImpl.search`:
  - `pattern = LikePatterns.contains(text)`;
  - `phoneDigits = digits.isEmpty() ? null : "%" + digits + "%"` (só dígitos, sem curinga possível);
  - `cpf = digits.length() == 11 ? digits : null`.
- **GOTCHA**:
  - Num text block Java, `'\\'` vira `'\'` no JPQL, que é o certo.
  - H2 (`MODE=PostgreSQL`) e PostgreSQL aceitam `escape '\'`.
  - O telefone continua com busca parcial, como a auditoria permite.
- **TEST** (`CustomerPrivacyTest`):
  - cliente com CPF `52998224725`: `?search=982247` não encontra; `?search=529.982.247-25` encontra;
  - cliente "Ana 100% Silva" e "Ana 1000": `?search=100%` traz só o primeiro; `?search=_` não traz todos.
- **VALIDATE**: os dois falham antes.

#### Task 6.3: PRO-03, busca de produtos (RED → GREEN)
- **IMPLEMENT**: separe em duas consultas para um plano estável:
  - `searchActive(category, lowStock, pageable)`: sem texto;
  - `search(pattern, barcode, category, lowStock, pageable)`: `lower(p.name) like :pattern escape '\\' or lower(p.brand) like :pattern escape '\\' or lower(p.sku) like :pattern escape '\\' or p.barcode = :barcode`.

  `ProductServiceImpl` escolhe a consulta pela presença de texto. Localize o método que chama `productRepository.search` e mantenha a assinatura pública.
- **TEST**: produtos `SKU-A_1` e `SKUXA11`: `?search=A_1` traz só o primeiro.
- **VALIDATE**: falha antes (traz os dois).

#### Task 6.4: CUS-02, anonimização completa (RED → GREEN)
- **IMPLEMENT**:
  - **customer**: `InstrumentRepository` ganha `@Modifying update versioned InstrumentEntity i set i.serialNumber = null, i.conditionNotes = null, i.updatedAt = :now where i.customerId = :customerId`. Chame em `CustomerServiceImpl.anonymize`, antes de publicar o evento.
  - **service**: `com.luthier.service.listeners.CustomerAnonymizedListener` (pacote novo `listeners`, interno), no padrão ASYNC_LISTENER_PATTERN:
    ```java
    @Async @TransactionalEventListener
    public void on(CustomerAnonymized e) { TenantContext.runAs(e.tenantId(), () -> scrubber.scrub(e.customerId())); }
    ```
    O `Scrubber` interno `@Transactional` chama:
    - `ServiceOrderRepository.scrubFreeText(customerId, REMOVED, now)`: `update versioned ServiceOrderEntity o set o.reportedIssue = :removed, o.diagnosisNotes = null, o.technicianNotes = null, o.rejectionReason = null, o.updatedAt = :now where o.customerId = :customerId`;
    - `scrubTimeline(customerId)`: `update OrderTimelineEntity t set t.description = null where t.order.id in (select o.id from ServiceOrderEntity o where o.customerId = :customerId)`.
  - **notification**: no `scrubByCustomerId` (fase 5), acrescente `n.error = null`. A Task 5.3 já para de gravar o destinatário no `error`.
  - **legado pré-V4_1**: `V6_3__notification_customer_backfill.sql`:
    ```sql
    -- Fase 6, módulo notification (CUS-02): notificações anteriores à V4_1 não tinham customer_id
    -- e escapavam da anonimização. Preenche a partir da OS de origem (mesma luthieria).
    -- Pré-checagem (informativa): select count(*) from notification_tb where customer_id is null and related_type = 'service_order';
    update notification_tb n
       set customer_id = (select o.customer_id from service_order_tb o
                           where o.id = n.related_id and o.tenant_id = n.tenant_id)
     where n.customer_id is null and n.related_type = 'service_order';
    ```
- **GOTCHA**:
  - `OrderTimelineEntity` é `@Immutable` e a coluna `description` é `updatable = false`. O Hibernate 7 pode **recusar** o UPDATE HQL em entidade imutável. **Plano B:** `@Query(nativeQuery = true, value = "update service_order_timeline_tb set description = null where tenant_id = :tenantId and order_id in (select id from service_order_tb where tenant_id = :tenantId and customer_id = :customerId)")` com `TenantContext.require()`. SQL nativo não recebe o filtro do `@TenantId`, por isso o `tenant_id` explícito nos **dois** níveis é obrigatório. Teste os dois tenants.
  - Clientes já anonimizados antes deste deploy não são reprocessados. Registre em `docs/auditoria.md`, com a consulta para reprocessar manualmente.
  - Confira em `V2_0` o tamanho e o `not null` de `reported_issue` (`not null`, 2000) e use `"[removido a pedido do titular]"`.
- **TEST** (`CustomerPrivacyTest.anonymizationScrubsInstrumentsOrdersTimelineAndNotificationErrors`):
  1. cliente com instrumento (`serialNumber`, `conditionNotes`), OS com `reportedIssue` "cliente Lucas tel 11987654321", diagnóstico, nota na timeline e uma notificação `falhou` com `error` preenchido (por `JdbcTemplate`);
  2. `POST /customers/{id}/anonymize`;
  3. `await`: o instrumento não tem série nem notas; `GET /service-orders/{os}` traz o `reportedIssue` removido, sem diagnóstico, e as descrições da timeline nulas; o `error` da notificação está nulo.

  Controle: outra luthieria com a mesma estrutura fica intacta.
- **VALIDATE**: falha antes.

#### Task 6.5: docs
- `docs/api.md`: a busca de clientes por CPF exige os 11 dígitos. `docs/auditoria.md`: ✔ CUS-01/02/04 e PRO-03, com a nota do `pg_trgm` e dos clientes anonimizados antes do deploy. `docs/security-review.md`: linha de anonimização. `CLAUDE.md`: versões.

### Branch 7: `test/sh-06-platform-scope`

#### Task 7.1: regra de arquitetura (RED → GREEN)
- **ACTION**: `PlatformScopeArchitectureTest` com ArchUnit (já é dependência de teste no parent).
- **IMPLEMENT**:
  ```java
  /** SH-06: o escopo sem filtro de tenant só pode ser aberto por quem foi revisado para isso. */
  @Test
  void onlyAllowedClassesOpenThePlatformScope() {
    var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.luthier");
    noClasses()
        .that().haveNameNotMatching(
            "com\\.luthier\\.(notification\\.services\\.impl\\.NotificationRetentionJob"
            + "|service\\.services\\.impl\\.CustomerPortalServiceImpl"
            + "|shared\\.tenancy\\.TenantContext)(\\$.*)?")
        .should().callMethod(TenantContext.class, "callAsPlatform", Supplier.class)
        .because("callAsPlatform abre a visão de todas as luthierias (SH-01/SH-06)")
        .check(classes);
  }
  ```
- **GOTCHA**:
  - Lambdas e classes internas geram nomes com `$`. Por isso o `(\\$.*)?` no regex.
  - Antes de fixar a lista, confirme com `grep -rn callAsPlatform --include=*.java srv-*/src/main` que só existem esses chamadores (hoje: `NotificationRetentionJob:39`, `CustomerPortalServiceImpl:53`).
  - Confira a assinatura real de `callAsPlatform` em `TenantContext.java:78` (`Supplier<T>`).
- **TEST (RED)**: o próprio teste é a regressão. Prove o vermelho temporariamente com uma chamada em qualquer outra classe e remova antes do commit. Registre no relatório.
- **VALIDATE**: verde. Docs: registrar SH-06 em `docs/auditoria.md` e movê-lo no PRD.

---

## Testing Strategy

### Testes novos

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `OutboxSchemaTest` | `serialized_event` de 50 kB | Aceito | ✔ |
| `CnpjTest` | CNPJ válido, DV errado, todos iguais | true/false/false | ✔ |
| `UserManagementFlowTest` | Rebaixar a si mesmo, último ADMIN, 2 ADMINs, outro tenant | 409, 409, 200, 404 | ✔ |
| `PasswordHashPoolTest` | 3 cadastros e 3 resets paralelos, pool 2 | Todos concluem | concorrência |
| `AuditActorTest` | Login, cadastro, reset, ação autenticada | Ator = UUID, nunca e-mail ou `anonymousUser` | |
| `AuthFlowTest` | Refresh, reuso, logout, reset, expirado, inativo | 200/401/400 conforme o caso | ✔ |
| `RateLimitFilterTest` | 3 IPv6 do mesmo /64 com capacidade 2 | 429 na terceira | ✔ |
| `ServiceOrderEntityTest` (+) | Sair de aguardando sem resposta; cancelar com pagamento | REJEITADO; paidAmount mantido | |
| `ServiceOrderRulesFlowTest` | Peça inativa → pronto; cliente com OS → DELETE; 4 aberturas paralelas | 200 + baixa; 409; números 1..4 | concorrência |
| `NotificationDeliveryTest` | Reentrega; SMTP falha; SMTP lento; boas-vindas com phishing | 1 envio; `falhou` sem `@`; 202 < 500 ms; sem texto do usuário | ✔ |
| `LikePatternsTest` | `%`, `_`, `\` | Escapados | ✔ |
| `CustomerPrivacyTest` | CPF parcial; curinga; anonimização em 2 tenants | Não acha; literal; só o tenant do evento | ✔ |
| `PlatformScopeArchitectureTest` | Classes de produção | Só a allowlist chama `callAsPlatform` | |

### Edge Cases Checklist
- [x] Input vazio (busca sem texto; nota vazia no SRV-05; `document`/`phone` nulos)
- [x] Input máximo (outbox de 50 kB; `rejectionReason` de 1000)
- [x] Tipos inválidos (CNPJ/telefone com DDI; AJUSTE negativo)
- [x] Concorrência (primeira OS; ADMINs; Argon2 com pool pequeno; reentrega do outbox)
- [x] Falha de rede (SMTP lança exceção)
- [x] Permissão negada / outro tenant (404 em usuário de outra luthieria; anonimização só no tenant do evento)

---

## Validation Commands

### Static Analysis
```bash
./mvnw -B -ntp install -DskipTests
```
EXPECT: BUILD SUCCESS, sem erro de compilação em nenhum módulo.

### Unit Tests (por módulo)
```bash
./mvnw -pl srv-luthier-shared test
./mvnw -pl srv-luthier-security -am test -Dtest=RateLimitFilterTest -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -pl srv-luthier-service -am test -Dtest=ServiceOrderEntityTest -Dsurefire.failIfNoSpecifiedTests=false
```
EXPECT: verde.

### Testes da fase
```bash
./mvnw -pl srv-luthier-app -am test -Dtest='OutboxSchemaTest,UserManagementFlowTest,AuthFlowTest,AuditActorTest,PasswordHashPoolTest,ServiceOrderRulesFlowTest,NotificationDeliveryTest,CustomerPrivacyTest,PlatformScopeArchitectureTest,ModularityTest' -Dsurefire.failIfNoSpecifiedTests=false
```
EXPECT: verde. O `ModularityTest` garante que a SPI do CUS-03 não criou ciclo.

### Full Test Suite
```bash
./mvnw -B -ntp verify
```
EXPECT: verde em cada branch e numa worktree descartável com as 7 mergeadas (mesma técnica da fase 5).

### Database Validation (obrigatório: V6_0 a V6_3)
```bash
# Docker ligado
./mvnw -pl srv-luthier-app -am test -Dtest=PostgresMigrationTest -Dsurefire.failIfNoSpecifiedTests=false
```
EXPECT: verde. **Sem Docker, a fase não fecha** (regra 3 do PRD e o ⚠ do APP-02).

### Manual Validation
- [ ] `java -jar srv-luthier-app/target/srv-luthier-app-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev`: `POST /auth/forgot-password` responde na hora, e o log mostra o "E-mail não enviado (SMTP não configurado)" logo depois.
- [ ] Com `docker compose up`: o Mailpit recebe **um** e-mail por mudança de status, mesmo reiniciando a app com eventos pendentes.
- [ ] `/v3/api-docs` (dev) bate com `docs/openapi.json` (409 novos, 400 do register).

---

## Acceptance Criteria (do PRD, fase 6)
- [ ] Cada achado tem teste de regressão que falhava antes da correção. Os de caracterização (SRV-10, parte do SRV-11, SEC-12) estão marcados no relatório.
- [ ] SEC-12: `AuthFlowTest`, `UserManagementFlowTest` e teste do rate limit com capacidade real (`RateLimitFilterTest`, mais o `RateLimitBypassTest` existente).
- [ ] SEC-06: nenhuma operação deixa a luthieria sem ADMIN ativo (409).
- [ ] CUS-01: a busca por CPF só aceita os 11 dígitos.
- [ ] CUS-02: a anonimização limpa instrumentos, textos livres das OS e o campo `error` das notificações.
- [ ] SRV-05, SRV-06 e CUS-03 seguem a decisão A registrada no PRD.
- [ ] APP-02: DDL conferido contra o Modulith 2.1.1 e `PostgresMigrationTest` verde.
- [ ] Mudanças de contrato refletidas em `docs/api.md` e `docs/openapi.json`.
- [ ] `./mvnw -B -ntp verify` verde em cada branch; `ModularityTest` verde.
- [ ] Revisão de código e de segurança sem CRÍTICO/ALTO aberto (obrigatória nas branches 2, 3, 5 e 6).

## Completion Checklist
- [ ] Padrões seguidos: bean interno `@Transactional` com tenant aplicado antes, `ConflictException`/`NotFoundException`, records em `domain/records`
- [ ] Logs sem dado pessoal (e-mail mascarado, exceção de SMTP só pela classe)
- [ ] Migrations com cabeçalho e pré-checagem, rodando em H2 e PostgreSQL
- [ ] Nenhum `tenant_id` gravado à mão (o SQL nativo do plano B só **filtra**)
- [ ] `docs/auditoria.md` com ✔ nos 19 + SEC-19, SEC-20 e SH-06 registrados
- [ ] PRD: fase 6 com plano, relatório e branches; decisões registradas
- [ ] `CLAUDE.md`: versões (`V6_0`…`V6_3`), `eventId` no `ServiceOrderStatusChanged`, SPI `CustomerDeactivationGuard`
- [ ] Front avisado: 409 novos (usuários, cliente), 400 no cadastro, busca por CPF, `quoteStatus` ao sair de aguardando

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Fases 3/5 não mergeadas quando a implementação começar | Média | Alto | GATE 0 para tudo |
| UPDATE HQL em `OrderTimelineEntity` (`@Immutable`) recusado pelo Hibernate 7 | Média | Médio | Plano B nativo com `tenant_id` explícito (Task 6.4) |
| Corrida da primeira OS não reproduzível no H2 | Alta | Baixo | Loop de 5 luthierias; o vermelho probabilístico fica registrado |
| Eventos antigos no outbox sem `eventId` | Certa no deploy | Baixo | `null` = comportamento antigo (Task 5.3) |
| Versões V6_x fora de ordem entre branches | Média | Médio | Ordem de merge 1 → 4 → 5 → 6; deploy só com todas mergeadas |
| `afterCommit` não dispara com `jakarta.transaction.Transactional` | Baixa | Médio | O teste do SEC-04 verifica o envio; se falhar, troque a anotação para a do Spring |
| ArchUnit acusa classes sintéticas de lambda | Média | Baixo | Regex com `(\$.*)?` (Task 7.1) |
| Branch 2 grande (6 achados) | Média | Médio | Commits por achado dentro da branch; revisão de segurança dedicada |

## Notes
- **SEC-19 entra aqui:** o PRD o sugeria para a fase 1, mas a fase 1 foi mergeada sem ele (`TenantRegistrationServiceImpl.register` ainda gera o Argon2 dentro de `@Transactional` em `develop`). É o mesmo defeito do SEC-20 e tem a mesma correção.
- **Outbox (APP-02):** o mapeamento JPA do Modulith 2.1.1 foi lido do jar local. A `V1_1` original não tinha nada incompatível além do tamanho. O índice hash oficial do PostgreSQL ficou de fora para manter uma migration única H2/PostgreSQL.
- **SPI do CUS-03:** é a primeira inversão de dependência entre módulos do projeto. Vale uma linha no `CLAUDE.md` (seção "Modules and boundaries") explicando o padrão.
- **Tabela de fases do PRD desatualizada:** as fases 1, 2 e 4 já foram mergeadas em `develop` (PRs #14 a #20), mas o PRD ainda mostra `pending`/`in-progress`. Os critérios de PostgreSQL das fases 2 e 5 continuam abertos.
