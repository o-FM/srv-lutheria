# Plan: Fase 3, integridade (SRV-01, PRO-01, SH-02, APP-01)

## Summary
Fechar os quatro furos de integridade da auditoria:
- **SRV-01:** a OS nunca tem total ou saldo negativos, e só recebe pagamento depois que o orçamento é aprovado.
- **PRO-01:** a baixa de estoque é idempotente por OS diante da reentrega do outbox.
- **SH-02:** violações de constraint do banco viram 409, não 500, e a trilha de auditoria não fica com registros "fantasma".
- **APP-01:** a aplicação deixa de subir no perfil dev com segredos públicos quando ninguém define o perfil.

## User Story
Como dono de uma luthieria, quero que totais, saldos e estoque nunca fiquem inconsistentes e que erros de concorrência voltem como conflito tratável, para confiar nos relatórios financeiros e no estoque sem conferência manual.

## Problem → Solution
| Achado | Hoje | Depois |
|---|---|---|
| SRV-01 | Remover itens depois do desconto deixa o total negativo. O pagamento é aceito em qualquer status exceto `CANCELADO`. | Invariante `desconto ≤ subtotal` e `pago ≤ total` em toda mudança. Remoção de item recusada se houver pagamento. Pagamento só em `EM_EXECUCAO`, `PRONTO` ou `ENTREGUE`. |
| PRO-01 | Reentregar `StockConsumptionRequested` baixa o estoque duas vezes. | Um marcador único por `(tenant_id, order_id)` garante uma baixa por OS. A reentrega é ignorada com log. |
| SH-02 | `DataIntegrityViolationException` vira 500, e a auditoria `REQUIRES_NEW` já gravada fica órfã. | 409 para violação de constraint (SQLState `23xxx`) e 400 para dado inválido (`22xxx`). Nos cadastros, `saveAndFlush` antes de publicar a auditoria. |
| APP-01 | `spring.profiles.default: dev` sobe o perfil permissivo, com o `JWT_SECRET` de dev público, quando não há perfil. | Sem perfil padrão. Os testes ativam o dev por `src/test/resources/config/application.yaml`, e `spring-boot:run` e a documentação ativam o dev explicitamente. |

## Metadata
- **Complexity:** Large (cerca de 15 arquivos, 4 achados independentes)
- **Source PRD:** `docs/correcoes.prd.md`
- **PRD Phase:** Fase 3, Integridade
- **Estimated Files:** 15 (8 de produção, 1 migration, 6 de teste ou config de teste)

---

## Pré-requisito (bloqueante)

A fase 2 (`fix/sh-01-tenant-fail-closed`) precisa estar **mergeada em `develop`** antes de criar a branch desta fase:
- A migration nova é a `V4_7`, porque a fase 2 ocupa `V4_2` a `V4_6`.
- A tabela nova precisa de FK `tenant_id → tenant_tb`; o `TenantIsolationTest` da fase 2 falha sem ela.
- O PRO-01 usa `TenantContext.runAs` com o comportamento fail-closed da fase 2.

Se a fase 2 ainda não estiver mergeada, crie a branch a partir de `fix/sh-01-tenant-fail-closed` e faça rebase sobre `develop` depois do merge dela.

**Branches**, uma por achado, cada uma com o próprio PR:

| Branch | Achado | Base |
|---|---|---|
| `fix/srv-01-order-totals` | SRV-01 | `develop` |
| `fix/pro-01-idempotent-stock-consumption` | PRO-01 | `develop`, depois da fase 2 |
| `fix/sh-02-integrity-409` | SH-02 | `develop` |
| `fix/app-01-no-default-profile` | APP-01 | `develop` |

SRV-01, SH-02 e APP-01 não dependem da fase 2; só o PRO-01 depende.

---

## UX Design

### Before / After
N/A: mudança interna da API. Os únicos efeitos visíveis para o front (`srv-lutheria-front`) estão abaixo.

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `POST /service-orders/{id}/payments` antes da aprovação (`recebido` … `aguardando_aprovacao`) | 201 | **409** "Pagamentos só depois da aprovação do orçamento" | **Quebra o "sinal antes do orçamento"**, se o front oferecer isso. Avisar o time do front. |
| `DELETE /service-orders/{id}/services/{item}` e `.../parts/{part}` quando o desconto passaria do novo subtotal | 200, com total negativo | **409** | Mensagem: "Remova ou reduza o desconto antes de remover este item" |
| Remoção de item com pagamento registrado | 200 | **409** | Inalcançável com a regra de pagamento nova; fica como defesa |
| Corrida em cadastro (e-mail, CPF, SKU, slug) ou FK violada | 500 | **409** problem+json genérico | Sem detalhe de constraint na resposta |
| Valor maior que a coluna (ex.: SEC-08) | 500 | **400** problem+json genérico | A validação de entrada continua no SEC-08 (fase 6) |
| `java -jar` sem `--spring.profiles.active` | Sobe em dev (H2, segredos públicos) | Não ativa o dev; sem `JWT_SECRET`, falha na subida | `docs` e `CLAUDE.md` passam a mostrar `--spring.profiles.active=dev` |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `srv-luthier-service/src/main/java/com/luthier/service/domain/entities/ServiceOrderEntity.java` | 149-333 | Toda regra do SRV-01: itens, desconto, orçamento, pagamento, totais, `requireEditable` |
| P0 | `srv-luthier-service/src/main/java/com/luthier/service/domain/enums/OrderStatus.java` | all | Máquina de estados: `next()`, `isEditable()`, `isOpen()` |
| P0 | `srv-luthier-product/src/main/java/com/luthier/product/services/impl/StockConsumptionListener.java` | all | Padrão `runAs` antes da transação e o `Consumer` interno que o PRO-01 altera |
| P0 | `srv-luthier-shared/src/main/java/com/luthier/shared/errors/GlobalExceptionHandler.java` | 72-97 | Padrão dos handlers 409; o SH-02 acrescenta um |
| P1 | `srv-luthier-product/src/main/java/com/luthier/product/services/impl/StockLedger.java` | 32-55 | `apply(...)` com `Propagation.MANDATORY`; **não muda** |
| P1 | `srv-luthier-product/src/main/java/com/luthier/product/domain/entities/StockMovementEntity.java` | 1-80 | Padrão de entidade `TenantScopedEntity` com factory estática, a copiar no marcador |
| P1 | `srv-luthier-product/src/main/resources/db/migration/product/V1_3__product_schema.sql` | 28-45 | Estilo de DDL do módulo |
| P1 | `srv-luthier-security/src/main/java/com/luthier/security/services/impl/TenantRegistrationServiceImpl.java` | 44-76 | `save` seguido de `publishEvent(AuditEvent)`: ponto do SH-02 |
| P1 | `srv-luthier-security/src/main/java/com/luthier/security/services/impl/UserManagementServiceImpl.java` | 55-66 | Mesmo padrão em `create` |
| P1 | `srv-luthier-app/src/main/resources/application.yaml` | 1-10 | `spring.profiles.default: dev`, que o APP-01 remove |
| P2 | `srv-luthier-service/src/main/java/com/luthier/service/services/impl/ServiceOrderServiceImpl.java` | 134-160, 197-199 | `changeStatus` publica `StockConsumptionRequested` (itens agrupados por produto) e `receivePayment` |
| P2 | `srv-luthier-app/src/test/java/com/luthier/ServiceOrderFlowTest.java` | 26-60, 207-223 | Fixtures (`setUp`), helpers `moveTo` e `payment` |
| P2 | `srv-luthier-app/src/test/java/com/luthier/TenantIsolationTest.java` (fase 2) | all | Teste de metadados de FK e helper `RetentionJobFlowTest.tenantOf(token)` |
| P2 | `docs/api.md` | 30-60 | Matriz de rotas e regras da OS a atualizar |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Spring Boot config locations | Spring Boot reference, "External Application Properties" | `classpath:/` e `classpath:/config/` são **ambos** carregados, com `config/` por cima. O arquivo de teste não substitui o `application.yaml` principal. |
| SQLState | SQL standard / PostgreSQL error codes | Classe `23` = integrity constraint violation (`23505` unique, `23503` FK, `23514` check, `23502` not null). Classe `22` = data exception (`22001` string too long). O H2 usa os mesmos códigos. |
| Spring Modulith outbox | Spring Modulith 2.1 reference, "Event Publication Registry" | Entrega ao menos uma vez. O listener que lança exceção deixa a publicação incompleta e ela é reprocessada. Um listener que retorna normalmente marca a publicação como concluída (`completion-mode: delete`). |

---

## Patterns to Mirror

### NAMING_CONVENTION / ENTITY
// SOURCE: srv-luthier-product/src/main/java/com/luthier/product/domain/entities/StockMovementEntity.java:20-60
```java
@Entity
@Table(name = "STOCK_MOVEMENT_TB", indexes =
    @Index(name = "idx_movement_tenant_product", columnList = "tenant_id, product_id"))
public class StockMovementEntity extends TenantScopedEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "product_id", nullable = false, updatable = false) private UUID productId;
  ...
  public static StockMovementEntity record(UUID productId, MovementType type, int quantity, ...)
```
- Tabela `*_TB` em maiúsculas na anotação e minúsculas na migration.
- Construtor `protected` vazio e factory estática com verbo (`record`, `of`, `open`).

### ERROR_HANDLING (domínio)
// SOURCE: ServiceOrderEntity.java:197-199, 273-282
```java
if (!status.isOpen()) {
  throw new ConflictException("OS encerrada não pode ser alterada");
}
...
if (amount.compareTo(balance()) > 0) {
  throw new ConflictException("Valor maior que o saldo devedor de R$ " + balance());
}
```
- `ConflictException` (409) para regra de estado.
- `BadRequestException` (400) para entrada inválida em si; ex.: `ServiceOrderEntity.java:207-209`, desconto maior que o total.
- `NotFoundException` (404) para item inexistente.
- Mensagens em pt-BR.

### ERROR_HANDLING (handler web)
// SOURCE: srv-luthier-shared/src/main/java/com/luthier/shared/errors/GlobalExceptionHandler.java:72-81
```java
@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
  return ResponseEntity.status(HttpStatus.CONFLICT)
      .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
          "Registro alterado por outra pessoa. Recarregue e tente novamente."));
}
```

### LOGGING_PATTERN
// SOURCE: srv-luthier-product/src/main/java/com/luthier/product/services/impl/StockConsumptionListener.java:24, 33-38
```java
private static final Logger log = LoggerFactory.getLogger(StockConsumptionListener.class);
...
log.info("Baixa de estoque concluída para {} ({} itens)", event.reference(), event.items().size());
```
SLF4J com placeholders e mensagem em pt-BR. Nunca logar dados pessoais.

### SERVICE_PATTERN (tenant antes da transação)
// SOURCE: StockConsumptionListener.java:33-62
```java
@Async
@TransactionalEventListener
public void on(StockConsumptionRequested event) {
  TenantContext.runAs(event.tenantId(), () -> consumer.consume(event));
  ...
}

@Component
static class Consumer {
  @Transactional
  public void consume(StockConsumptionRequested event) {
    for (var item : event.items()) {
      var product = productRepository.findById(item.productId())
          .orElseThrow(() -> new NotFoundException("Product not found: " + item.productId()));
      stockLedger.apply(product, MovementType.SAIDA, item.quantity(), event.reference(),
          event.orderId(), event.requestedBy());
    }
  }
}
```

### REPOSITORY_PATTERN
// SOURCE: srv-luthier-product/src/main/java/com/luthier/product/domain/repositories/StockMovementRepository.java:10-15
```java
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovementEntity, UUID> {
  Page<StockMovementEntity> findAllByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);
}
```
Derived queries. Nunca filtrar `tenant_id` à mão, porque o Hibernate filtra.

### MIGRATION_PATTERN
// SOURCE: srv-luthier-product/src/main/resources/db/migration/product/V1_3__product_schema.sql:28-45 e customer/V4_3__tenant_fk.sql (fase 2)
```sql
create table stock_movement_tb (
    id             uuid          not null primary key,
    tenant_id      uuid          not null,
    ...
    created_at     timestamp with time zone not null,
    updated_at     timestamp with time zone not null,
    version        bigint        not null,
    constraint fk_movement_product foreign key (product_id) references product_tb (id),
```

### TEST_STRUCTURE (integração)
// SOURCE: srv-luthier-app/src/test/java/com/luthier/ServiceOrderFlowTest.java:26-55, 207-223
```java
@SpringBootTest(properties = {
    "app.security.rate-limit.login.capacity=1000",
    "app.security.rate-limit.portal.capacity=1000"})
@AutoConfigureMockMvc
class ServiceOrderFlowTest {
  @BeforeEach void setUp() throws Exception {
    api = new ApiClient(mvc);
    admin = api.registerWorkshop("Oficina OS");
    customerId = api.create(admin, "/customers", "{\"name\":\"Lucas Silveira\",\"phone\":\"(11) 98765-4321\"}");
    ...
  }
  private ResultActions payment(String orderId, String amount) throws Exception {
    return mvc.perform(auth(post("/service-orders/" + orderId + "/payments"), admin)
        .content("{\"amount\":%s,\"method\":\"pix\"}".formatted(amount)));
  }
```
Efeitos assíncronos são conferidos com `await().atMost(Duration.ofSeconds(10)).untilAsserted(...)`, como no `ServiceOrderFlowTest.java:101-104`.

### TEST_STRUCTURE (unitário)
// SOURCE: srv-luthier-shared/src/test/java/com/luthier/shared/validation/CpfTest.java
JUnit 5 + AssertJ, sem Spring, classe package-private com nomes de método descritivos. O módulo `srv-luthier-service` **não tem `src/test`**; é preciso criar a pasta. As dependências de teste vêm do `srv-luthier-parent` (`spring-boot-starter-test`).

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `srv-luthier-service/.../domain/entities/ServiceOrderEntity.java` | UPDATE | SRV-01: invariantes, remoção bloqueada com pagamento, regra de status do pagamento |
| `srv-luthier-service/.../domain/enums/OrderStatus.java` | UPDATE | SRV-01: `acceptsPayment()` |
| `srv-luthier-service/src/test/java/com/luthier/service/domain/entities/ServiceOrderEntityTest.java` | CREATE | SRV-01: testes de entidade sem Spring |
| `srv-luthier-product/.../domain/entities/StockConsumptionEntity.java` | CREATE | PRO-01: marcador de baixa aplicada |
| `srv-luthier-product/.../domain/repositories/StockConsumptionRepository.java` | CREATE | PRO-01 |
| `srv-luthier-product/.../services/impl/StockConsumptionListener.java` | UPDATE | PRO-01: idempotência |
| `srv-luthier-product/src/main/resources/db/migration/product/V4_7__stock_consumption.sql` | CREATE | PRO-01: tabela com unicidade e FK de tenant |
| `srv-luthier-app/src/test/java/com/luthier/StockConsumptionIdempotencyTest.java` | CREATE | PRO-01: reentrega do evento |
| `srv-luthier-shared/.../errors/GlobalExceptionHandler.java` | UPDATE | SH-02: handler de `DataIntegrityViolationException` |
| `srv-luthier-shared/src/test/java/com/luthier/shared/errors/GlobalExceptionHandlerTest.java` | CREATE | SH-02: MockMvc standalone |
| `srv-luthier-security/.../services/impl/TenantRegistrationServiceImpl.java` | UPDATE | SH-02: `saveAndFlush` antes da auditoria |
| `srv-luthier-security/.../services/impl/UserManagementServiceImpl.java` | UPDATE | SH-02: idem em `create` |
| `srv-luthier-app/src/main/resources/application.yaml` | UPDATE | APP-01: remover `profiles.default` |
| `srv-luthier-app/src/test/resources/config/application.yaml` | CREATE | APP-01: testes ativam o dev |
| `srv-luthier-app/src/test/java/com/luthier/ProfileDefaultsTest.java` | CREATE | APP-01: regressão |
| `srv-luthier-app/pom.xml` | UPDATE | APP-01: `spring-boot:run` com perfil dev |
| `ServiceOrderFlowTest.java` | UPDATE | SRV-01: casos de integração (pagamento antes da aprovação, remoção depois do desconto) |
| `docs/api.md`, `CLAUDE.md`, `README.md`, `docs/auditoria.md`, `docs/correcoes.prd.md` | UPDATE | Contrato novo, comando de execução local, status |

## NOT Building

- **Estorno ou reembolso** ao cancelar OS com pagamento: `EM_EXECUCAO` com pagamento ainda pode ser cancelada. Registrar como achado novo, se a regra for desejada.
- **SEC-19** (deadlock no `/auth/register`): o SH-02 só reordena o `saveAndFlush`; a transação longa do cadastro continua.
- **PRO-06** (o `orderId` livre nos movimentos manuais): o desenho do PRO-01 não depende dele.
- **Backfill** do marcador para OS antigas: substituído por uma pré-checagem do outbox no deploy.
- **CHECKs monetários** no banco (SRV-07) e índice em `stock_movement_tb.order_id`: ficam na fase 5.
- **Proteção de segredo de dev por valor**, que recusaria a subida se `JWT_SECRET` fosse o de dev em prod: fica como opcional, não incluído.

---

## Step-by-Step Tasks

### Task 1 (SRV-01): testes vermelhos de entidade
- **ACTION:** criar `srv-luthier-service/src/test/java/com/luthier/service/domain/entities/ServiceOrderEntityTest.java`, no mesmo pacote da entidade, para acessar as factories `OrderServiceItemEntity.of` e `OrderPartEntity.of`, que são package-private.
- **IMPLEMENT:** helper `order()` = `ServiceOrderEntity.open(1, UUID.randomUUID(), UUID.randomUUID(), Priority.NORMAL, "Trastes", null, "tok", "user")`. Casos:
  1. `removingAServiceBelowTheDiscountIsRejected`: serviço de 100 e peça de 50 (1 × 50), desconto 120 via `updateDetails(..., new BigDecimal("120"), null)`; remover a peça lança `ConflictException`, e `grandTotal()` continua `30`, sem mudança.
  2. `removingAPartBelowTheDiscountIsRejected`: o simétrico.
  3. `removingAnItemThatKeepsTheDiscountValidWorks`: desconto 10; remover a peça deixa `grandTotal() == 90`.
  4. `paymentBeforeQuoteApprovalIsRejected`: em `RECEBIDO`, `DIAGNOSTICO`, `ORCAMENTO` e `AGUARDANDO_APROVACAO` (via `sendQuote`), `receivePayment` lança `ConflictException`.
  5. `paymentAfterApprovalIsAccepted`: `sendQuote` e `approveQuote`, depois `receivePayment(50)` dá `balance() == 100` (150 − 50).
  6. `paymentOnDeliveredOrderIsAccepted`: o caminho até `ENTREGUE` aceita pagamento do saldo.
  7. `itemsCannotBeRemovedOnceAPaymentExists`: defesa redundante. Como a regra de status a torna inalcançável pela API, testar via `assertTotalsConsistent`, ou documentar que fica coberta pelos casos 4 e 5. Ver GOTCHA.
  8. `quoteWithDiscountAboveSubtotalCannotBeSent`: simula dado legado com `ReflectionTestUtils.setField(order, "discount", new BigDecimal("999"))`; `sendQuote` lança `ConflictException`.
- **MIRROR:** TEST_STRUCTURE (unitário).
- **IMPORTS:** `org.junit.jupiter.api.Test`, `static org.assertj.core.api.Assertions.*`, `org.springframework.test.util.ReflectionTestUtils`, `com.luthier.shared.errors.ConflictException`, `com.luthier.service.domain.enums.*`, `java.math.BigDecimal`, `java.util.UUID`.
- **GOTCHA:**
  - Os itens só têm `id` depois de persistidos. `removeService(itemId)` compara com `getId()`, que é nulo no teste. Depois de `addService` ou `addPart`, use `ReflectionTestUtils.setField(item, "id", UUID.randomUUID())`.
  - `approveQuote` exige `quoteStatus == PENDENTE`, que só o `sendQuote` define.
  - Para chegar a `ENTREGUE`: `changeStatus(PRONTO, ...)` e depois `changeStatus(ENTREGUE, ...)`. Na entidade não há checagem de estoque nem de bancada; isso fica no service.
- **VALIDATE:** `./mvnw -pl srv-luthier-service test -Dtest=ServiceOrderEntityTest` deve **FALHAR**: os casos 1, 2, 4 e 8 ficam vermelhos.

### Task 2 (SRV-01): implementar os invariantes
- **ACTION:** alterar `OrderStatus` e `ServiceOrderEntity`.
- **IMPLEMENT:**
  - `OrderStatus`:
    ```java
    /** Pagamento só depois do orçamento aprovado (decisão SRV-01, docs/correcoes.prd.md). */
    public boolean acceptsPayment() {
      return this == EM_EXECUCAO || this == PRONTO || this == ENTREGUE;
    }
    ```
  - `ServiceOrderEntity.receivePayment`: troca a checagem de `CANCELADO` por
    `if (!status.acceptsPayment()) throw new ConflictException("Pagamentos só depois da aprovação do orçamento");`.
    Antes de `log`, troca o `paidAmount.add` + `if (amount > balance)` atual por `assertTotalsConsistent()` depois de somar, ou mantém a checagem atual, que já protege o pagamento.
  - `removeService` e `removePart`:
    1. `requireEditable()`;
    2. `requireNoPayments()`;
    3. localiza o item (senão `NotFoundException`, mesma mensagem de hoje);
    4. calcula `subtotalAfter = laborTotal().add(partsTotal()).subtract(totalDoItem)`;
    5. se `discount.compareTo(subtotalAfter) > 0`, lança `ConflictException("Remova ou reduza o desconto antes de remover este item")`;
    6. só então remove.

    **Checar antes de mutar:** a coleção em memória não fica alterada se a regra falhar.
  - `requireNoPayments()`: `if (paidAmount.signum() > 0) throw new ConflictException("OS com pagamento registrado não pode ter itens removidos");`.
  - `sendQuote`: antes do `transition`, chama `assertTotalsConsistent()`.
  - `private void assertTotalsConsistent()`:
    - se `discount > laborTotal + partsTotal`, lança `ConflictException("Desconto maior que o subtotal da OS")`;
    - se `paidAmount > grandTotal()`, lança `ConflictException("Valor pago maior que o total da OS")`.
  - `updateDetails` continua lançando `BadRequestException` para desconto maior que o total, porque é entrada inválida (400), e chama `assertTotalsConsistent()` depois de atribuir o desconto.
- **MIRROR:** ERROR_HANDLING (domínio).
- **IMPORTS:** nenhum novo; `ConflictException` e `NotFoundException` já são importados.
- **GOTCHA:**
  - Não alterar `isEditable()`, para manter a edição de itens só até `ORCAMENTO`.
  - O total do serviço é `getLaborPrice()`; o da peça é `total()`.
  - `ServiceOrderStatusChanged` e os relatórios leem `balance()`; com o invariante, ele nunca fica negativo.
- **VALIDATE:** o `ServiceOrderEntityTest` fica verde, e `./mvnw -pl srv-luthier-app -am test -Dtest=ServiceOrderFlowTest -Dsurefire.failIfNoSpecifiedTests=false` continua verde, porque o `fullLifecycle` paga em `PRONTO`.

### Task 3 (SRV-01): integração e contrato
- **ACTION:** acrescentar ao `ServiceOrderFlowTest`:
  - `paymentBeforeApprovalIsRejected`: `payment(newOrder(), "10.00")` dá 409;
  - `removingAnItemBelowTheDiscountIsRejected`: adiciona serviço e peça, aplica desconto acima do valor que sobraria, e o `DELETE` do item dá 409 e mantém o total.
- **IMPLEMENT:** confira as rotas de remoção de itens no `ServiceOrderController` (`DELETE /service-orders/{id}/services/{itemId}` e `/parts/{partId}`) antes de escrever o teste. Em `docs/api.md`:
  - na linha de `/payments`, a nota "só a partir de `em_execucao` (orçamento aprovado)";
  - nas regras da OS, os bullets "desconto ≤ subtotal; remover item que quebre isso dá 409" e "pagamento só a partir de em_execucao".
- **VALIDATE:** `ServiceOrderFlowTest` verde.

### Task 4 (PRO-01): teste vermelho de reentrega
- **ACTION:** criar `srv-luthier-app/src/test/java/com/luthier/StockConsumptionIdempotencyTest.java`.
- **IMPLEMENT:**
  - `@SpringBootTest(properties = "app.security.rate-limit.login.capacity=1000") @AutoConfigureMockMvc`;
  - cria a luthieria e um produto com `initialQuantity` 5, e extrai `tenantId` com `RetentionJobFlowTest.tenantOf(token)` (helper da fase 2);
  - monta `new StockConsumptionRequested(tenantId, orderId = UUID.randomUUID(), "OS #99", "teste", List.of(new Item(productId, 2)))`;
  - chama `listener.on(event)` **duas vezes**, com `StockConsumptionListener` injetado (é `public`; a chamada passa pelo proxy `@Async`, que é o que se quer);
  - `await` até `currentQuantity == 3` via `GET /products/{id}` e confirma que **continua 3** depois de uma espera curta (`Awaitility.await().during(Duration.ofSeconds(2))`);
  - `GET /products/{id}/movements` tem exatamente 1 movimento com `orderId`.
- **MIRROR:** TEST_STRUCTURE (integração).
- **GOTCHA:** a chamada direta a `on(...)` não passa pelo outbox, o que basta para provar a idempotência do consumidor. Não publique o evento por `ApplicationEventPublisher` fora de uma transação: `@TransactionalEventListener` ignora eventos sem transação ativa.
- **VALIDATE:** o teste deve **FALHAR**, com saldo 1 em vez de 3.

### Task 5 (PRO-01): marcador idempotente
- **ACTION:** migration, entidade, repositório e listener.
- **IMPLEMENT:**
  - `V4_7__stock_consumption.sql`:
    ```sql
    -- PRO-01: uma baixa de estoque por OS. O outbox do Modulith entrega o evento ao menos uma
    -- vez; o marcador único faz a reentrega ser ignorada. Antes do deploy, confira que não há
    -- baixa pendente no outbox (select count(*) from event_publication where completion_date
    -- is null and listener_id like '%StockConsumptionListener%' deve dar 0), porque as baixas
    -- antigas não têm marcador.
    create table stock_consumption_tb (
        id          uuid          not null primary key,
        tenant_id   uuid          not null,
        order_id    uuid          not null,
        reference   varchar(255)  not null,
        created_at  timestamp with time zone not null,
        updated_at  timestamp with time zone not null,
        version     bigint        not null,
        constraint uk_stock_consumption_order unique (tenant_id, order_id),
        constraint fk_stock_consumption_tenant foreign key (tenant_id) references tenant_tb (id)
    );
    ```
  - `StockConsumptionEntity extends TenantScopedEntity`: `@Table(name = "STOCK_CONSUMPTION_TB", uniqueConstraints = @UniqueConstraint(name = "uk_stock_consumption_order", columnNames = {"tenant_id", "order_id"}))`; campos `id`, `orderId`, `reference`; `public static StockConsumptionEntity of(UUID orderId, String reference)`.
  - `StockConsumptionRepository extends JpaRepository<StockConsumptionEntity, UUID>` com `boolean existsByOrderId(UUID orderId);`.
  - `Consumer.consume`: retorna `boolean`.
    1. Se `repository.existsByOrderId(event.orderId())`, retorna `false`.
    2. `repository.saveAndFlush(StockConsumptionEntity.of(event.orderId(), event.reference()))`. O marcador vai **antes** dos movimentos, para uma entrega concorrente esbarrar na unicidade cedo.
    3. Loop atual de `stockLedger.apply`.
    4. Retorna `true`.
  - `on(event)`:
    ```java
    try {
      boolean applied = TenantContext.callAs(event.tenantId(), () -> consumer.consume(event));
      if (applied) log.info("Baixa de estoque concluída para {} ({} itens)", ...);
      else log.info("Baixa de estoque de {} já aplicada; reentrega ignorada", event.reference());
    } catch (DataIntegrityViolationException e) {
      // entrega concorrente gravou o marcador primeiro
      log.info("Baixa de estoque de {} já aplicada por outra entrega", event.reference());
    }
    ```
    Retornar normalmente faz o Modulith marcar a publicação como concluída.
- **MIRROR:** NAMING_CONVENTION / ENTITY, SERVICE_PATTERN, MIGRATION_PATTERN.
- **IMPORTS:** `org.springframework.dao.DataIntegrityViolationException`, `com.luthier.product.domain.entities.StockConsumptionEntity`, `com.luthier.product.domain.repositories.StockConsumptionRepository`.
- **GOTCHA:**
  - **Não** use `(order_id, product_id, type)` em `stock_movement_tb`, como a auditoria sugere. O movimento manual aceita `orderId` livre (`StockMovementRequest.orderId`, PRO-06): uma SAIDA manual ligada à OS faria a baixa real ser pulada, ou a migration falharia com dado existente.
  - O `catch` precisa ficar **fora** do `callAs`/`consume`: a transação já está marcada para rollback quando o flush falha.
  - Uma falha por saldo insuficiente (`ConflictException` no `apply`) desfaz também o marcador, e o outbox reprocessa. Esse é o comportamento correto, mantido.
  - O `TenantIsolationTest` (fase 2) exige a FK de tenant, que já está na DDL.
- **VALIDATE:** o `StockConsumptionIdempotencyTest` fica verde. `ServiceOrderFlowTest`, `TenantIsolationTest` e `ModularityTest` também ficam verdes.

### Task 6 (SH-02): teste vermelho do handler
- **ACTION:** criar `srv-luthier-shared/src/test/java/com/luthier/shared/errors/GlobalExceptionHandlerTest.java`.
- **IMPLEMENT:**
  - `MockMvcBuilders.standaloneSetup(new FailingController()).setControllerAdvice(new GlobalExceptionHandler()).build()`;
  - `@RestController static class FailingController` com `@GetMapping("/unique")`, que lança `new DataIntegrityViolationException("x", new SQLException("dup", "23505"))`, e `@GetMapping("/too-long")`, que usa `"22001"`;
  - espera **409** com `$.detail` igual a "Registro duplicado ou em conflito. Recarregue e tente novamente." e `Content-Type: application/problem+json`, e **400** para `22001`;
  - confere que a resposta **não** contém `"dup"` nem nome de constraint.
- **GOTCHA:** confirme que o `srv-luthier-shared` tem `spring-webmvc` e `spring-test` no classpath de teste; vêm do parent (web e test libs). Se o standalone reclamar do conversor de `ProblemDetail`, use `.setMessageConverters(new MappingJackson2HttpMessageConverter())`, ou o equivalente do Jackson 3 no Boot 4.
- **VALIDATE:** `./mvnw -pl srv-luthier-shared test -Dtest=GlobalExceptionHandlerTest` deve **FALHAR** com 500.

### Task 7 (SH-02): handler e ordem dos cadastros
- **IMPLEMENT:**
  - No `GlobalExceptionHandler`, depois do handler de lock otimista:
    ```java
    /**
     * Constraint do banco violada (unicidade, FK, CHECK): em geral, duas requisições passaram juntas
     * pela checagem "existe?" (SH-02). Dado que não cabe na coluna (SQLState 22xxx) é 400.
     * Nunca devolve o nome da constraint nem a mensagem do driver.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
      if (NestedExceptionUtils.getMostSpecificCause(ex) instanceof SQLException sql
          && sql.getSQLState() != null && sql.getSQLState().startsWith("22")) {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Dado inválido ou maior que o permitido."));
      }
      return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(
          HttpStatus.CONFLICT, "Registro duplicado ou em conflito. Recarregue e tente novamente."));
    }
    ```
  - `TenantRegistrationServiceImpl.register`: troca `userRepository.save(...)` por `userRepository.saveAndFlush(...)` antes dos `publishEvent`. O flush grava o tenant e o usuário, e a unicidade dispara **antes** da auditoria `REQUIRES_NEW`.
  - `UserManagementServiceImpl.create`: o mesmo, com `saveAndFlush` antes do `AuditEvent.of("USER_CREATED", ...)`.
- **IMPORTS:** `org.springframework.dao.DataIntegrityViolationException`, `org.springframework.core.NestedExceptionUtils`, `java.sql.SQLException`.
- **GOTCHA:**
  - O `ObjectOptimisticLockingFailureException` **não** é subclasse de `DataIntegrityViolationException`, então os handlers não colidem.
  - Não logar `ex.getMessage()`, que contém valores e pode ter dado pessoal; no máximo `log.debug` com a classe.
  - O `UserManagementServiceImpl` usa `jakarta.transaction.Transactional` (SEC-15). Não troque agora; o `saveAndFlush` funciona igual.
- **VALIDATE:** o `GlobalExceptionHandlerTest` fica verde. O `RegistrationFlowTest` (cadastro duplicado continua 409 pela checagem prévia) e o `PrivacyAndSecurityTest` continuam verdes.

### Task 8 (APP-01): teste vermelho e remoção do perfil padrão
- **ACTION:**
  1. Criar `ProfileDefaultsTest` (unitário, sem Spring):
     ```java
     @Test
     void baseConfigurationDoesNotActivateAnyProfileByDefault() throws IOException {
       var sources = new YamlPropertySourceLoader().load("base", new ClassPathResource("application.yaml"));
       assertThat(sources).allSatisfy(s -> assertThat(s.getProperty("spring.profiles.default")).isNull());
     }
     ```
     Ele fica **vermelho**, porque hoje o valor é `dev`.
  2. Remover as linhas `profiles:` / `default: dev` do `srv-luthier-app/src/main/resources/application.yaml`, e ajustar o comentário da linha 2 ("perfil padrão local" vira "perfil dev, ativado explicitamente").
  3. Criar `srv-luthier-app/src/test/resources/config/application.yaml`:
     ```yaml
     # Só para testes: ativa o perfil dev (H2), que deixou de ser o padrão (APP-01).
     spring:
       profiles:
         active: dev
     ```
  4. No `srv-luthier-app/pom.xml`, configurar o `spring-boot-maven-plugin` com `<configuration><profiles><profile>dev</profile></profiles></configuration>`, que só vale para `spring-boot:run`.
  5. Documentação:
     - `CLAUDE.md` (bloco "Run locally") e `README.md:31`: `java -jar srv-luthier-app/target/srv-luthier-app-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev`;
     - remover "default profile" do texto;
     - na seção de perfis do `CLAUDE.md`, dizer que o dev não é mais o padrão.
- **GOTCHA:**
  - **Não** crie `src/test/resources/application.yaml`: ele *substituiria* o principal no classpath de teste. A pasta `config/` é carregada em adição.
  - O `ProfileDefaultsTest` lê `classpath:application.yaml`, que no classpath de teste é o principal; o de teste está em `config/`.
  - O `PostgresMigrationTest` também passa a ativar o dev pelo arquivo de teste, como hoje acontece pelo padrão. Ele sobrescreve o datasource via Testcontainers.
  - O Dockerfile já fixa `SPRING_PROFILES_ACTIVE=prod`; não muda.
  - Na IDE, os testes leem `config/application.yaml` normalmente.
- **VALIDATE:**
  - o `ProfileDefaultsTest` fica verde e a suíte inteira passa;
  - manual: `java -jar …jar` sem perfil e sem `JWT_SECRET` falha na subida por placeholder não resolvido;
  - com `--spring.profiles.active=dev`, sobe na porta 8080.

### Task 9: documentação e status
- `docs/auditoria.md`: linha "**Status:** resolvido na branch …" em SRV-01, PRO-01, SH-02 e APP-01. No PRO-01, registrar o desvio de desenho (marcador por OS em vez do índice em movimentos).
- `docs/correcoes.prd.md`: marcar a fase 3 como `done` só depois do merge dos 4 PRs.
- `docs/openapi.json`: se a geração for manual, conferir as descrições de 409 em `/payments` e nas remoções de itens.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| ServiceOrderEntityTest: remover abaixo do desconto | serviço 100, peça 50, desconto 120, remover peça | 409, total 30 inalterado | sim |
| ServiceOrderEntityTest: remover mantendo invariante | desconto 10, remover peça | total 90 | não |
| ServiceOrderEntityTest: pagamento antes da aprovação | status RECEBIDO…AGUARDANDO_APROVACAO | 409 em cada | sim |
| ServiceOrderEntityTest: pagamento após aprovação | EM_EXECUCAO, 50 de 150 | saldo 100 | não |
| ServiceOrderEntityTest: pagamento em ENTREGUE | saldo restante | aceito | sim |
| ServiceOrderEntityTest: orçamento com desconto legado inválido | desconto 999 | 409 no sendQuote | sim |
| GlobalExceptionHandlerTest | SQLState 23505 / 23503 / 22001 | 409 / 409 / 400, sem vazar detalhes | sim |
| ProfileDefaultsTest | application.yaml base | sem `spring.profiles.default` | não |

### Integration Tests

| Test | Cenário | Esperado |
|---|---|---|
| ServiceOrderFlowTest.paymentBeforeApprovalIsRejected | OS nova, pagamento 10 | 409 |
| ServiceOrderFlowTest.removingAnItemBelowTheDiscountIsRejected | desconto alto e DELETE do item | 409, total mantido |
| StockConsumptionIdempotencyTest | mesmo evento duas vezes | saldo baixa uma vez, 1 movimento |
| Suíte existente | — | sem regressões; o `fullLifecycle` paga em `PRONTO` |

### Edge Cases Checklist
- [x] Desconto igual ao subtotal (total 0): permitido, porque a comparação é `>` e não `>=`
- [x] Pagamento igual ao saldo: permitido (já testado no `fullLifecycle`)
- [x] Reentrega concorrente: `DataIntegrityViolationException` tratada como "já aplicada"
- [x] Falha por saldo insuficiente: rollback do marcador e reprocessamento
- [x] Dado legado inconsistente (desconto acima do subtotal): `sendQuote` recusa
- [ ] Cancelamento de OS `EM_EXECUCAO` com pagamento: fora do escopo (ver NOT Building)

---

## Validation Commands

### Static Analysis
```bash
./mvnw -B -ntp -q compile
```
EXPECT: sem erros de compilação

### Unit Tests
```bash
./mvnw -pl srv-luthier-service test -Dtest=ServiceOrderEntityTest
./mvnw -pl srv-luthier-shared test -Dtest=GlobalExceptionHandlerTest
```
EXPECT: tudo verde

### Integration Tests
```bash
./mvnw -pl srv-luthier-app -am test -Dtest='ServiceOrderFlowTest,StockConsumptionIdempotencyTest,ProfileDefaultsTest,TenantIsolationTest,ModularityTest' -Dsurefire.failIfNoSpecifiedTests=false
```
EXPECT: tudo verde. Ler `srv-luthier-app/target/surefire-reports/com.luthier.<Classe>.txt`, porque o log tem muito SQL.

### Full Test Suite
```bash
./mvnw -B -ntp verify
```
EXPECT: BUILD SUCCESS, sem regressões

### Database Validation
```bash
# com Docker ligado
./mvnw -pl srv-luthier-app -am test -Dtest=PostgresMigrationTest -Dsurefire.failIfNoSpecifiedTests=false
```
EXPECT: `V4_7` aplicada no PostgreSQL. **Obrigatório** antes do merge do PRO-01.

### Manual Validation
- [ ] `java -jar srv-luthier-app/target/srv-luthier-app-1.0.0-SNAPSHOT.jar`, sem perfil e sem `JWT_SECRET`, falha ao subir
- [ ] o mesmo jar com `--spring.profiles.active=dev` sobe; o login `admin@luthieria.local` / `dev-admin-12345` funciona
- [ ] `./mvnw -pl srv-luthier-app spring-boot:run` sobe em dev

---

## Acceptance Criteria
- [ ] Todos os critérios da fase 3 em `docs/correcoes.prd.md`
- [ ] Os testes novos falharam antes da implementação, com o vermelho registrado no PR
- [ ] `./mvnw -B -ntp verify` verde e `PostgresMigrationTest` verde com Docker
- [ ] Revisões de código e de segurança sem CRÍTICO ou ALTO
- [ ] `docs/api.md`, `CLAUDE.md`, `README.md` e `docs/auditoria.md` atualizados

## Completion Checklist
- [ ] Mensagens de erro em pt-BR; `ConflictException` para estado, `BadRequestException` para entrada
- [ ] Nenhum `tenant_id` manual; o marcador usa `TenantScopedEntity`
- [ ] Migration `V4_7` na pasta do módulo product, com FK de tenant e comentário de pré-checagem
- [ ] Nenhum detalhe de SQL ou constraint na resposta HTTP
- [ ] Nada além do escopo (ver NOT Building)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| O front oferece "sinal" antes da aprovação e passa a receber 409 | Média | Médio | Avisar o time do front no PR; mensagem 409 clara |
| Baixas antigas sem marcador são reentregues depois do deploy | Baixa | Alto (estoque baixado duas vezes) | Pré-checagem do outbox (comentário da `V4_7`) antes do deploy |
| Remover `profiles.default` quebra scripts locais ou de carga que usam `java -jar` | Média | Baixo | Documentar `--spring.profiles.active=dev`; `spring-boot:run` já configurado |
| No H2, a unicidade concorrente do marcador se comporta diferente do PostgreSQL | Baixa | Baixo | O teste de reentrega é sequencial; o tratamento de `DataIntegrityViolationException` cobre o concorrente |
| A fase 2 ainda não está mergeada quando a fase 3 começa | Alta | Médio | Pré-requisito explícito; rebase |

## Notes
- **Decisões de negócio do SRV-01**, tomadas pelo usuário em 2026-11-07 e registradas no PRD:
  1. desconto ≤ subtotal validado em toda edição e no envio do orçamento, **e** remoção de itens proibida quando houver pagamento;
  2. pagamento só depois da aprovação (`EM_EXECUCAO`, `PRONTO`, `ENTREGUE`).
- Com a regra 2, a proibição de remover itens com pagamento fica **inalcançável** pela máquina de estados atual. Os itens só são editáveis até `ORCAMENTO`, e de `EM_EXECUCAO` não se volta. A proibição continua como defesa, caso a máquina de estados mude.
- O **buraco real** que sobra no SRV-01 é o "desconto depois de remover itens", coberto pela checagem antes da remoção.
- O PRO-01 desvia da sugestão da auditoria por causa do PRO-06. Registrar o motivo na linha de status da auditoria.
