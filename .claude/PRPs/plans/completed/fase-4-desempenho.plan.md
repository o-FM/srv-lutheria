# Plan: Fase 4, desempenho (SRV-02, SRV-03, APP-04)

## Summary
Tirar o N+1 das duas leituras mais pesadas da OS e ligar o batch do Hibernate:
- **APP-04:** `default_batch_fetch_size`, `jdbc.batch_size`, `order_inserts` e `order_updates` em `application.yaml`. É uma mitigação global, para o N+1 que ainda não foi tratado (SRV-10, `OrderResponse.from`) e para os UPDATEs linha a linha.
- **SRV-02:** a listagem de OS (kanban) deixa de carregar `services` e `parts`. Os totais da página vêm de **uma** consulta JPQL agregada.
- **SRV-03:** o "a receber" do relatório financeiro vira **uma** consulta JPQL escalar. Nenhuma entidade é carregada e a soma acontece no banco.

## User Story
Como atendente de uma luthieria, quero que o kanban e o relatório financeiro respondam rápido mesmo com anos de histórico, para não esperar a tela nem derrubar o serviço nas horas de pico.

## Problem → Solution
| Achado | Hoje | Depois |
|---|---|---|
| SRV-02 | `OrderResponse.summary()` chama `grandTotal()` e `balance()`, que percorrem `services` e `parts` (LAZY). Uma página de 20 OS faz cerca de 40 SELECTs extras. | `search` faz count + página + **1** consulta de totais por `o.id in :ids`. Nenhuma coleção é inicializada. |
| SRV-03 | `findBillable()` carrega **todas** as OS `EM_EXECUCAO`/`PRONTO`/`ENTREGUE` como entidades e soma `balance()` em Java, com N+1. O custo cresce com todo o histórico. | `sumReceivable()` devolve um `BigDecimal`: a soma no banco dos saldos positivos, com subqueries correlacionadas e o filtro de tenant do Hibernate. São 0 entidades e 1 consulta. |
| APP-04 | Sem batch: cada coleção LAZY é um SELECT e cada INSERT/UPDATE é uma ida ao banco. | Batch fetch de 50, batch JDBC de 50 e INSERTs e UPDATEs ordenados. |

## Metadata
- **Complexity:** Medium (cerca de 10 arquivos, 3 achados, sem migration)
- **Source PRD:** `docs/correcoes.prd.md`
- **PRD Phase:** Fase 4, Desempenho
- **Estimated Files:** 10 (5 de produção, 1 config, 2 testes novos, k6 e docs)

---

## Pré-requisito e branches

A fase 3 (`fix/srv-01-order-totals`, `fix/sh-02-integrity-409`, `fix/app-01-no-default-profile`, `fix/pro-01-idempotent-stock-consumption`) **ainda não foi mergeada** em `develop` (situação em 2026-11-07). As fases 1 e 2 já foram (PRs #14 a #17).

- **Dependência real:** só semântica. O "a receber" agregado pressupõe saldo nunca negativo (SRV-01). A consulta nova mantém o filtro `saldo > 0`, então o resultado é o mesmo antes e depois do SRV-01.
- **Conflitos esperados com a fase 3:**
  - `ServiceOrderEntity.grandTotal()`: o SRV-01 introduz `subtotal()`. Esta fase **não altera** a entidade (ver Task 5), então não deve haver conflito.
  - `application.yaml`: o APP-01 remove `spring.profiles.default` (linhas 6-7). O APP-04 mexe no bloco `jpa` (linhas 11-15). São hunks diferentes.
  - **Batch JDBC e SH-02:** ver o GOTCHA da Task 1.
- **Regra:** crie as branches a partir de `develop` **depois** do merge da fase 3. Se ela ainda não tiver sido mergeada, crie-as a partir de `develop` e faça rebase depois.

| Branch | Achado | Base | Ordem |
|---|---|---|---|
| `perf/app-04-hibernate-batch` | APP-04 | `develop` | qualquer |
| `perf/srv-02-order-list-totals` | SRV-02 | `develop` | qualquer |
| `perf/srv-03-receivable-aggregate` | SRV-03 | `develop` | qualquer |

As três são independentes. O teste do SRV-02 **não** pode depender da ausência do APP-04: ele confere que as coleções não são carregadas, e não só que o número de consultas é constante, porque o batch fetch sozinho já deixaria esse número constante.

---

## UX Design

### Before / After
N/A: mudança interna. O contrato JSON não muda (`totalAmount`, `paidAmount` e `balance` continuam na listagem; `receivable` continua no financeiro).

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `GET /service-orders?…` | ~2 SELECTs por OS da página | 3 SELECTs por página (count, página, totais) | Mesmo JSON. `services`, `parts` etc. continuam `[]` na listagem |
| `GET /reports/financial` | Carrega o histórico inteiro de OS faturáveis | 1 SELECT escalar para `receivable` | Mesmo valor (saldos positivos somados) |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `srv-luthier-service/src/main/java/com/luthier/service/domain/records/OrderResponse.java` | 21-67 | `from()` (detalhe, não muda) e `summary()` (listagem, muda) |
| P0 | `srv-luthier-service/src/main/java/com/luthier/service/services/impl/ServiceOrderServiceImpl.java` | 84-90 | `search(...)`: ponto do SRV-02 |
| P0 | `srv-luthier-service/src/main/java/com/luthier/service/domain/repositories/ServiceOrderRepository.java` | 31-38, 66-76 | `search` (página) e `findBillable` (sai) |
| P0 | `srv-luthier-service/src/main/java/com/luthier/service/services/impl/ReportServiceImpl.java` | 76-106 | `financial(...)`: ponto do SRV-03 |
| P0 | `srv-luthier-service/src/main/java/com/luthier/service/domain/entities/ServiceOrderEntity.java` | 93-129, 294-309 | `discount`, `paidAmount`, coleções e as fórmulas `laborTotal/partsTotal/grandTotal/balance` |
| P1 | `srv-luthier-service/src/main/java/com/luthier/service/domain/entities/OrderPartEntity.java` | 21-57 | `order` (ManyToOne), `unitPrice`, `quantity`, `total()` |
| P1 | `srv-luthier-service/src/main/java/com/luthier/service/domain/entities/OrderServiceItemEntity.java` | 18-42 | `order`, `laborPrice` |
| P1 | `srv-luthier-service/src/main/java/com/luthier/service/domain/repositories/PaymentRepository.java` | all | Estilo de agregação JPQL do módulo |
| P1 | `srv-luthier-app/src/main/resources/application.yaml` | 3-15 | Bloco `spring.jpa`, onde o APP-04 entra |
| P1 | `srv-luthier-app/src/test/java/com/luthier/ServiceOrderFlowTest.java` | 26-54, 60-113, 213-223 | Fixtures, ciclo até `em_execucao` pelo portal, helpers `moveTo`/`payment` |
| P2 | `srv-luthier-shared/src/main/java/com/luthier/shared/web/PageResponse.java` | all | `PageResponse.of(page, mapper)` |
| P2 | `srv-luthier-service/src/main/resources/db/migration/service/V2_0__service_orders.sql` | 45-78 | Índices `idx_order_tenant_status`, `idx_order_item_order` e `idx_order_part_order`, que as subqueries usam |
| P2 | `load-tests/k6-luthieria.js` | 85-100 | Grupo "balcão: consultas", onde entra o financeiro |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Batch fetch e batch JDBC | Hibernate ORM 7.x User Guide, "Batching" e "Fetching > Batch fetching" | `hibernate.default_batch_fetch_size` vale para coleções e proxies LAZY. `hibernate.jdbc.batch_size` não agrupa INSERT com `GenerationType.IDENTITY`; aqui todos os IDs são `UUID`, então agrupa. |
| Statistics | Hibernate ORM 7.x, `org.hibernate.stat.Statistics` | Ativar com `hibernate.generate_statistics=true`. `getPrepareStatementCount()`, `getCollectionFetchCount()`, `getEntityStatistics(name).getLoadCount()` e `clear()`. As estatísticas são **globais** à `SessionFactory`, então listeners assíncronos também contam. |
| HQL literais e projeções | Hibernate ORM 7.x, "HQL > Literals" e "select new" | Literal BigDecimal com sufixo `bd` (`0bd`). `select new <record>(...)` casa o construtor pelos tipos. `coalesce(x, 0)` com literal inteiro pode virar `Integer`, então use `0bd`. |
| `@TenantId` | Hibernate ORM 7.x, "Multitenancy > @TenantId" | A restrição de tenant entra em toda referência à entidade na HQL. Nas subqueries correlacionadas por `i.order = o`, o `o` externo já está filtrado, então não há como vazar dados de outro tenant. |

Verificação local feita no planejamento: H2 2.4.240 com `MODE=PostgreSQL` aceita `sum(coalesce((select sum(...) where x.oid = o.id), 0) + ...)` com subquery correlacionada **dentro** do `sum` e no `where`. O resultado foi conferido à mão (101,00, com o pedido de saldo negativo excluído). O PostgreSQL também aceita subquery dentro do argumento de agregação (só proíbe agregação aninhada).

---

## Patterns to Mirror

### REPOSITORY_PATTERN (agregação JPQL com text block)
// SOURCE: srv-luthier-service/src/main/java/com/luthier/service/domain/repositories/PaymentRepository.java:15-21
```java
  /** [método, soma, quantidade] no período. */
  @Query("""
      select p.method, sum(p.amount), count(p) from PaymentEntity p
      where p.createdAt >= :from and p.createdAt < :to
      group by p.method
      """)
  List<Object[]> sumByMethod(@Param("from") Instant from, @Param("to") Instant to);
```

### REPOSITORY_PATTERN (enum totalmente qualificado em JPQL)
// SOURCE: srv-luthier-service/src/main/java/com/luthier/service/domain/repositories/ServiceOrderRepository.java:66-76
```java
  /** A receber: OS prontas/entregues com saldo em aberto (total calculado no Java). */
  @Query("""
      select o from ServiceOrderEntity o
      where o.status in (com.luthier.service.domain.enums.OrderStatus.PRONTO,
                         com.luthier.service.domain.enums.OrderStatus.ENTREGUE,
                         com.luthier.service.domain.enums.OrderStatus.EM_EXECUCAO)
      """)
  List<ServiceOrderEntity> findBillable();

  @Query("select coalesce(sum(o.paidAmount), 0) from ServiceOrderEntity o")
  BigDecimal sumPaid();
```

### SERVICE_PATTERN (leitura paginada)
// SOURCE: srv-luthier-service/src/main/java/com/luthier/service/services/impl/ServiceOrderServiceImpl.java:84-90
```java
  @Override
  @Transactional(readOnly = true)
  public PageResponse<OrderResponse> search(OrderStatus status, UUID customerId, Integer number,
      Pageable pageable) {
    return PageResponse.of(orderRepository.search(status, customerId, number, pageable),
        OrderResponse::summary);
  }
```

### RECORD / DTO (records em `domain/records`, factories estáticas)
// SOURCE: srv-luthier-service/src/main/java/com/luthier/service/domain/records/OrderResponse.java:60-67
```java
  /** Versão enxuta para listagens. */
  public static OrderResponse summary(ServiceOrderEntity o) {
    return new OrderResponse(o.getId(), o.getOrderNumber(), o.getCustomerId(),
        o.getInstrumentId(), o.getStatus(), o.getPriority(), o.getReportedIssue(), null, null,
        o.getCreatedAt(), o.getEstimatedDeliveryDate(), o.getCompletedAt(), o.getDeliveredAt(),
        List.of(), List.of(), null, List.of(), List.of(), o.grandTotal(), o.getPaidAmount(),
        o.balance(), null);
  }
```

### NAMING_CONVENTION
- Repositório: verbo + objeto (`sumByMethod`, `sumPaid`, `countOverdue`). Os novos são `totalsOf(ids)` e `sumReceivable()`.
- Record de leitura em `domain/records`, com Javadoc de uma linha em pt-BR.
- Comentários e mensagens em pt-BR; identificadores em inglês.

### ERROR_HANDLING
Nenhum caminho de erro novo. As consultas são somente leitura dentro de `@Transactional(readOnly = true)`, já presente em `search` e na classe `ReportServiceImpl`.

### LOGGING_PATTERN
O módulo service não usa logger (`LoggerFactory` não aparece em `srv-luthier-service/src/main/java`). **Não acrescente log.**

### TEST_STRUCTURE
// SOURCE: srv-luthier-app/src/test/java/com/luthier/ServiceOrderFlowTest.java:26-54, 213-223
```java
@SpringBootTest(properties = {
    "app.security.rate-limit.login.capacity=1000",
    "app.security.rate-limit.portal.capacity=1000"})
@AutoConfigureMockMvc
class ServiceOrderFlowTest {
  @Autowired MockMvc mvc;
  ApiClient api; String admin; ...
  @BeforeEach
  void setUp() throws Exception {
    api = new ApiClient(mvc);
    admin = api.registerWorkshop("Oficina OS");
    customerId = api.create(admin, "/customers",
        "{\"name\":\"Lucas Silveira\",\"phone\":\"(11) 98765-4321\"}");
    instrumentId = api.create(admin, "/customers/" + customerId + "/instruments",
        "{\"type\":\"guitarra\",\"brand\":\"Fender\",\"model\":\"Stratocaster\"}");
    productId = api.create(admin, "/products", """
        {"name":"Encordoamento 0.10","category":"cordas","sku":"EXL110","unit":"jogo",
         "costPrice":25,"salePrice":45,"minQuantity":2,"initialQuantity":3}""");
    catalogId = api.create(admin, "/service-catalog",
        "{\"title\":\"Regulagem completa\",\"laborPrice\":180.00,\"estimatedDays\":3}");
  }
  ...
  private ResultActions moveTo(String orderId, String to) throws Exception {
    return mvc.perform(auth(post("/service-orders/" + orderId + "/status"), admin)
        .content("{\"status\":\"%s\"}".formatted(to)));
  }
  private ResultActions payment(String orderId, String amount) throws Exception {
    return mvc.perform(auth(post("/service-orders/" + orderId + "/payments"), admin)
        .content("{\"amount\":%s,\"method\":\"pix\"}".formatted(amount)));
  }
```
Caminho até `em_execucao`, de `ServiceOrderFlowTest.java:67-97`: `moveTo diagnostico` → `moveTo orcamento` → (itens/desconto) → `POST /service-orders/{id}/quote/send` (lê `$.clientTrackingToken`) → `POST /portal/orders/{token}/quote/approve`, que leva a `em_execucao`. Pagamento só a partir de `em_execucao` (regra do SRV-01). `entregue` **não** exige saldo zero (`ServiceOrderEntity.java:234-236`).

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `srv-luthier-app/src/main/resources/application.yaml` | UPDATE | APP-04: propriedades de batch do Hibernate |
| `srv-luthier-service/src/main/java/com/luthier/service/domain/records/OrderTotals.java` | CREATE | SRV-02: projeção `(orderId, laborTotal, partsTotal)` e as fórmulas de total e saldo |
| `srv-luthier-service/src/main/java/com/luthier/service/domain/repositories/ServiceOrderRepository.java` | UPDATE | SRV-02: `totalsOf(ids)`. SRV-03: `sumReceivable()` entra e `findBillable()` sai |
| `srv-luthier-service/src/main/java/com/luthier/service/domain/records/OrderResponse.java` | UPDATE | SRV-02: `summary(o, totals)` |
| `srv-luthier-service/src/main/java/com/luthier/service/services/impl/ServiceOrderServiceImpl.java` | UPDATE | SRV-02: `search` busca os totais da página |
| `srv-luthier-service/src/main/java/com/luthier/service/services/impl/ReportServiceImpl.java` | UPDATE | SRV-03: `receivable = orders.sumReceivable()`; sai o import de `ServiceOrderEntity` |
| `srv-luthier-app/src/test/java/com/luthier/OrderListQueryCountTest.java` | CREATE | SRV-02: regressão (sem carga de coleção, consultas constantes, totais iguais ao detalhe) |
| `srv-luthier-app/src/test/java/com/luthier/FinancialReceivableTest.java` | CREATE | SRV-03: valor igual ao cálculo atual, isolamento de tenant, 0 entidades carregadas |
| `load-tests/k6-luthieria.js` | UPDATE | Incluir `GET /reports/financial` no grupo do balcão |
| `docs/auditoria.md` | UPDATE | Marcar SRV-02, SRV-03 e APP-04 como resolvidos (✔), uma marca em cada branch |

## NOT Building

- **Materializar `total_amount` na OS** (alternativa da auditoria): exige migration, backfill e manter a coluna em sincronia na entidade. A agregação basta para o critério do PRD. Fica registrada como evolução, caso o relatório ainda pese com dezenas de milhares de OS.
- **SRV-10** (N+1 na exportação LGPD): é da fase 6. O APP-04 só o mitiga.
- **Otimizar `OrderResponse.from()`** (detalhe de uma OS): com o batch fetch, já fica em poucas consultas por OS. Fora do escopo.
- **Novos índices:** os de `order_id` nas tabelas de item e peça e o de `(tenant_id, status)` já existem (`V2_0`). A fase 5 cuida do restante.
- **Mudanças de contrato** em `docs/api.md` ou `docs/openapi.json`: nenhuma.
- **`sumPaid()`:** não é usado pelo relatório atual. Não mexa nele.

---

## Step-by-Step Tasks

### Task 1: APP-04, batch do Hibernate (branch `perf/app-04-hibernate-batch`)
- **ACTION:** acrescentar as propriedades em `application.yaml`, dentro de `spring.jpa`.
- **IMPLEMENT:**
  ```yaml
    jpa:
      open-in-view: false
      hibernate:
        # O schema é responsabilidade do Flyway; o Hibernate só confere se bate com as entidades.
        ddl-auto: validate
      properties:
        hibernate:
          # Mitiga N+1 em coleções LAZY (APP-04): carrega até 50 coleções/proxies por SELECT.
          default_batch_fetch_size: 50
          # Agrupa INSERT/UPDATE em lotes (IDs são UUID, então o batch funciona).
          jdbc.batch_size: 50
          order_inserts: true
          order_updates: true
  ```
- **MIRROR:** comentários de uma linha em pt-BR, como no bloco `flyway`.
- **IMPORTS:** nenhum.
- **GOTCHA:**
  1. Com `jdbc.batch_size`, a exceção de constraint passa a vir de um `BatchUpdateException`. O `GlobalExceptionHandler.handleIntegrity` do SH-02 usa `NestedExceptionUtils.getMostSpecificCause(ex)` e lê o `SQLState`. No H2 e no PgJDBC o `BatchUpdateException` carrega o SQLState da falha, mas, se vier `null`, o handler cai no 409, o que **ainda é correto** para violação de constraint (23xxx). O único risco é um 22001 (valor longo demais) virar 409 em vez de 400. Depois que a fase 3 for mergeada e esta branch tiver o rebase, rode `GlobalExceptionHandlerTest`, `RegistrationFlowTest` e `StockConsumptionIdempotencyTest` (este usa `DataIntegrityViolationException` para detectar a reentrega).
  2. `@Version` com batch de UPDATE: o Hibernate 7 confere o update count por linha do lote. O `ObjectOptimisticLockingFailureException` continua saindo. Os testes de concorrência existentes cobrem esse caso.
  3. Não há teste novo que falhe antes desta mudança (é configuração). O critério do PRD é "suíte inteira verde". Como teste mínimo, um `@SpringBootTest` que lê `spring.jpa.properties.hibernate.default_batch_fetch_size` do `Environment` **não** é exigido. Basta a suíte verde.
- **VALIDATE:** `./mvnw -B -ntp verify` verde.

### Task 2: SRV-02, teste vermelho (branch `perf/srv-02-order-list-totals`)
- **ACTION:** criar `srv-luthier-app/src/test/java/com/luthier/OrderListQueryCountTest.java`.
- **IMPLEMENT:**
  - `@SpringBootTest(properties = {"app.security.rate-limit.login.capacity=1000", "app.security.rate-limit.portal.capacity=1000", "spring.jpa.properties.hibernate.generate_statistics=true"})` + `@AutoConfigureMockMvc`.
  - `@Autowired MockMvc mvc; @Autowired EntityManagerFactory emf;` e `Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();`.
  - `setUp` igual ao `ServiceOrderFlowTest` (oficina, cliente, instrumento, produto com `initialQuantity` alto, por exemplo 100, e serviço de catálogo 180,00).
  - Helper `openOrder()`: cria a OS com o serviço de catálogo, faz `moveTo diagnostico` e `moveTo orcamento`, adiciona a peça (`quantity: 1`) e aplica `PATCH {"discount":10}`. Os itens ficam editáveis, sem orçamento, sem pagamento e sem evento assíncrono de estoque. Total = 180 + 45 − 10 = 215.
  - Helper `measureListing()`: `stats.clear()`, faz `GET /service-orders?size=20` e devolve `stats`.
  - **Teste 1 `listingDoesNotLoadOrderItems`:** cria 3 OS, mede, e confere:
    - `stats.getCollectionFetchCount() == 0`
    - `stats.getEntityStatistics("com.luthier.service.domain.entities.OrderServiceItemEntity").getLoadCount() == 0`
    - o mesmo para `"com.luthier.service.domain.entities.OrderPartEntity"`
    Hoje falha: `summary()` inicializa `services` e `parts`.
  - **Teste 2 `listingQueryCountDoesNotGrowWithPageSize`:** cria 2 OS e mede `n2 = getPrepareStatementCount()`; cria mais 10 e mede `n12`. `assertThat(n12).isEqualTo(n2)`. Envolva cada medição em `await().atMost(10s).untilAsserted(...)`, porque as estatísticas são globais e um listener assíncrono de outro teste pode somar SELECTs. Use `<=` contra a primeira medição estável, se necessário.
  - **Teste 3 `listingTotalsMatchDetail`:** para cada OS da página, `$.content[i].totalAmount` e `$.content[i].balance` iguais aos de `GET /service-orders/{id}` (215,00 e 215,00). Inclua uma OS **sem itens** (só aberta, sem serviço), que deve ter `totalAmount` 0 e `balance` 0. Esse caso prova o `getOrDefault` com zeros.
- **MIRROR:** TEST_STRUCTURE; asserts com `jsonPath` e AssertJ (`org.assertj.core.api.Assertions.assertThat`).
- **IMPORTS:** `jakarta.persistence.EntityManagerFactory`, `org.hibernate.SessionFactory`, `org.hibernate.stat.Statistics`, `static org.awaitility.Awaitility.await`, `java.time.Duration`, `com.jayway.jsonpath.JsonPath`, além dos estáticos de `MockMvcRequestBuilders`/`MockMvcResultMatchers` e `ApiClient.auth`.
- **GOTCHA:**
  - Referencie as entidades por **nome em string** (`getEntityStatistics(String)`), sem importar classes internas do módulo service no teste. Assim o teste não cria acoplamento com o pacote interno.
  - O teste 1 precisa falhar **mesmo com o APP-04** aplicado (o batch fetch carrega as coleções em lote, então `getCollectionFetchCount() > 0`). Por isso ele é o teste principal.
  - Contexto Spring novo por causa de `generate_statistics`. É aceitável.
- **VALIDATE:** `./mvnw -pl srv-luthier-app -am test -Dtest=OrderListQueryCountTest -Dsurefire.failIfNoSpecifiedTests=false`, com os testes 1 e 2 **vermelhos** e o teste 3 verde (caracterização).

### Task 3: SRV-02, projeção `OrderTotals`
- **ACTION:** criar `srv-luthier-service/src/main/java/com/luthier/service/domain/records/OrderTotals.java`.
- **IMPLEMENT:**
  ```java
  package com.luthier.service.domain.records;

  import java.math.BigDecimal;
  import java.util.UUID;

  /** Totais de itens de uma OS, calculados no banco (listagem sem carregar as coleções). */
  public record OrderTotals(UUID orderId, BigDecimal laborTotal, BigDecimal partsTotal) {

    public static OrderTotals empty(UUID orderId) {
      return new OrderTotals(orderId, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Mesma fórmula de ServiceOrderEntity.grandTotal(). */
    public BigDecimal grandTotal(BigDecimal discount) {
      return laborTotal.add(partsTotal).subtract(discount);
    }

    /** Mesma fórmula de ServiceOrderEntity.balance(). */
    public BigDecimal balance(BigDecimal discount, BigDecimal paidAmount) {
      return grandTotal(discount).subtract(paidAmount);
    }
  }
  ```
- **MIRROR:** RECORD / DTO.
- **GOTCHA:** a fórmula repete a da entidade. O teste 3 da Task 2 garante que as duas batem. **Não** mude a entidade para reaproveitar o record: o SRV-01 (fase 3) está alterando `grandTotal()`/`subtotal()` e isso criaria conflito.
- **VALIDATE:** compila.

### Task 4: SRV-02, consulta de totais da página
- **ACTION:** acrescentar em `ServiceOrderRepository`.
- **IMPLEMENT:**
  ```java
  /** Totais de mão de obra e peças das OS informadas, sem carregar as coleções (SRV-02). */
  @Query("""
      select new com.luthier.service.domain.records.OrderTotals(o.id,
          coalesce((select sum(i.laborPrice) from OrderServiceItemEntity i where i.order = o), 0bd),
          coalesce((select sum(p.unitPrice * p.quantity) from OrderPartEntity p where p.order = o), 0bd))
      from ServiceOrderEntity o
      where o.id in :ids
      """)
  List<OrderTotals> totalsOf(@Param("ids") Collection<UUID> ids);
  ```
- **MIRROR:** REPOSITORY_PATTERN (text block, `@Param`).
- **IMPORTS:** `com.luthier.service.domain.records.OrderTotals` (`Collection`, `List` e `UUID` já estão importados).
- **GOTCHA:**
  - Use o literal `0bd`, não `0`: com `0`, o tipo inferido pode ser `Integer` e o Hibernate não acha o construtor (`Cannot instantiate class ... no matching constructor`). Se o `0bd` não for aceito pelo parser, troque por `cast(0 as BigDecimal)`.
  - `p.unitPrice * p.quantity` (BigDecimal × int) dá BigDecimal na HQL do Hibernate 7.
  - Chamar com lista vazia gera `in ()`, que é inválido em alguns bancos. Por isso o serviço não chama a consulta quando a página está vazia (Task 5).
  - **Alternativa, se a projeção por construtor der problema:** devolva `List<Object[]>` (`select o.id, coalesce(...), coalesce(...)`) e monte o `OrderTotals` no serviço, como o `ReportServiceImpl` faz com `sumByMethod`.
- **VALIDATE:** a aplicação sobe. O Spring Data valida a JPQL na inicialização, então qualquer erro de sintaxe derruba o contexto já no primeiro teste.

### Task 5: SRV-02, `summary` e `search` usam os totais
- **ACTION:** mudar `OrderResponse.summary` e `ServiceOrderServiceImpl.search`.
- **IMPLEMENT:**
  - `OrderResponse`:
    ```java
    /** Versão enxuta para listagens: totais vêm do banco (OrderTotals), sem tocar nas coleções. */
    public static OrderResponse summary(ServiceOrderEntity o, OrderTotals totals) {
      return new OrderResponse(o.getId(), o.getOrderNumber(), o.getCustomerId(),
          o.getInstrumentId(), o.getStatus(), o.getPriority(), o.getReportedIssue(), null, null,
          o.getCreatedAt(), o.getEstimatedDeliveryDate(), o.getCompletedAt(), o.getDeliveredAt(),
          List.of(), List.of(), null, List.of(), List.of(), totals.grandTotal(o.getDiscount()),
          o.getPaidAmount(), totals.balance(o.getDiscount(), o.getPaidAmount()), null);
    }
    ```
    Remova a sobrecarga `summary(ServiceOrderEntity)`. O único chamador é `search` (confirmado com grep: só `ServiceOrderServiceImpl.java:89`).
  - `ServiceOrderServiceImpl.search`:
    ```java
    var page = orderRepository.search(status, customerId, number, pageable);
    if (page.isEmpty()) {
      return PageResponse.of(page, o -> OrderResponse.summary(o, OrderTotals.empty(o.getId())));
    }
    var totals = orderRepository.totalsOf(page.map(ServiceOrderEntity::getId).getContent())
        .stream().collect(Collectors.toMap(OrderTotals::orderId, Function.identity()));
    return PageResponse.of(page, o -> OrderResponse.summary(o,
        totals.getOrDefault(o.getId(), OrderTotals.empty(o.getId()))));
    ```
    Um caminho mais limpo para a página vazia é `PageResponse.of(page, o -> null)`, mas `of` mapeia uma lista vazia, então o mapper nunca é chamado. Prefira extrair o mapa num método privado `totalsOf(Page<ServiceOrderEntity>)` que devolve `Map.of()` quando `page.isEmpty()`. Assim fica um único `return`.
- **MIRROR:** SERVICE_PATTERN. `Collectors` já está importado em `ServiceOrderServiceImpl` (é usado na linha 151).
- **IMPORTS:** `com.luthier.service.domain.records.OrderTotals`, `java.util.Map`, `java.util.function.Function`.
- **GOTCHA:** **não** chame `o.grandTotal()`, `o.balance()`, `o.getServices()` nem `o.getParts()` no caminho da listagem. Qualquer um desses inicializa a coleção e o teste 1 volta a falhar.
- **VALIDATE:** `OrderListQueryCountTest` verde (3 de 3). `ServiceOrderFlowTest` verde.

### Task 6: SRV-03, teste vermelho (branch `perf/srv-03-receivable-aggregate`)
- **ACTION:** criar `srv-luthier-app/src/test/java/com/luthier/FinancialReceivableTest.java`.
- **IMPLEMENT:**
  - Mesmas anotações da Task 2, inclusive `generate_statistics=true`.
  - `setUp` como no `ServiceOrderFlowTest`, com produto `initialQuantity: 100`.
  - Helper `approvedOrder(String discount, boolean withPart)`: cria a OS com o serviço de 180, faz `diagnostico` e `orcamento`, adiciona a peça se pedido, aplica o desconto se pedido, envia o orçamento e aprova pelo portal. A OS fica em `em_execucao`. Devolve o id.
  - Cenário, com valores esperados calculados à mão:
    | OS | Situação | Saldo | Entra no "a receber"? |
    |---|---|---|---|
    | A | `em_execucao`, serviço 180 + peça 45 − desconto 25 = 200, pago 50 | 150 | sim |
    | B | `pronto` (sem peça, sem baixa assíncrona), 180, pago 0 | 180 | sim |
    | C | `entregue`, 180, pago 180 | 0 | não (saldo 0) |
    | D | `entregue`, 180, pago 100 (entrega não exige quitação) | 80 | sim |
    | E | `orcamento`, 180, sem pagamento | (180) | não (status não faturável) |
    | F | **outra luthieria** (`api.registerWorkshop("Outra")`), `em_execucao`, 180, pago 0 | 180 | não (outro tenant) |
    Esperado: `receivable == 150 + 180 + 80 = 410.00`.
  - **Teste 1 `receivableSumsOpenBalancesOfBillableOrders`:** `GET /reports/financial` com `$.receivable` igual a `410.0`. Verde já hoje (caracterização): prova que o valor não muda.
  - **Teste 2 `receivableDoesNotLoadOrders`:** `stats.clear()`, `GET /reports/financial` e então `stats.getEntityStatistics("com.luthier.service.domain.entities.ServiceOrderEntity").getLoadCount() == 0` e `stats.getCollectionFetchCount() == 0`. Hoje falha (`findBillable` carrega 4 OS e as coleções). Use `await().untilAsserted`, porque as transições de status publicam `ServiceOrderStatusChanged` e o listener assíncrono de notificação pode carregar entidades **do módulo notification**. Por isso o assert é sobre a entidade `ServiceOrderEntity` pelo nome, e o `getCollectionFetchCount` só entra se ficar estável. Se oscilar, remova esse assert e mantenha o de `ServiceOrderEntity`.
  - **Teste 3 `receivableQueryCountDoesNotGrowWithHistory`:** mede `getPrepareStatementCount()` do `GET /reports/financial`, cria mais 5 OS `entregue` quitadas e mede de novo. As duas medições devem ser iguais (com `await`, pelo mesmo motivo da Task 2).
- **MIRROR:** TEST_STRUCTURE. Leia o token do portal como em `ServiceOrderFlowTest.java:78-82` (`JsonPath.read(sent, "$.clientTrackingToken")`).
- **GOTCHA:**
  - Pagamento antes de `em_execucao` dá 409 depois do SRV-01. Pague sempre **depois** da aprovação pelo portal.
  - `pronto` exige `requireBench()` (perfil ADMIN ou LUTHIER; o admin serve) e estoque para as peças. Na OS B, não use peça, para não disparar o `StockConsumptionRequested` assíncrono.
  - Se esta branch for criada **antes** do merge do SRV-01, o pagamento em `em_execucao` também funciona (hoje o pagamento é aceito em qualquer status exceto `cancelado`). O cenário vale nos dois casos.
  - O `ServiceOrderEntity` é carregado pelas **escritas** do `setUp`. Por isso o `stats.clear()` vem logo antes do GET medido.
- **VALIDATE:** `-Dtest=FinancialReceivableTest`, com o teste 1 verde e os testes 2 e 3 vermelhos.

### Task 7: SRV-03, agregação `sumReceivable`
- **ACTION:** substituir `findBillable()` em `ServiceOrderRepository` e usar a consulta nova em `ReportServiceImpl.financial`.
- **IMPLEMENT:**
  - Repositório (remova `findBillable`; o único chamador era o relatório):
    ```java
    /**
     * A receber (SRV-03): soma, no banco, dos saldos positivos das OS em execução, prontas ou
     * entregues. Saldo = mão de obra + peças - desconto - pago. JPQL mantém o filtro de tenant.
     */
    @Query("""
        select coalesce(sum(
            coalesce((select sum(i.laborPrice) from OrderServiceItemEntity i where i.order = o), 0bd)
          + coalesce((select sum(p.unitPrice * p.quantity) from OrderPartEntity p where p.order = o), 0bd)
          - o.discount - o.paidAmount), 0bd)
        from ServiceOrderEntity o
        where o.status in (com.luthier.service.domain.enums.OrderStatus.PRONTO,
                           com.luthier.service.domain.enums.OrderStatus.ENTREGUE,
                           com.luthier.service.domain.enums.OrderStatus.EM_EXECUCAO)
          and coalesce((select sum(i.laborPrice) from OrderServiceItemEntity i where i.order = o), 0bd)
            + coalesce((select sum(p.unitPrice * p.quantity) from OrderPartEntity p where p.order = o), 0bd)
            - o.discount - o.paidAmount > 0
        """)
    BigDecimal sumReceivable();
    ```
  - `ReportServiceImpl.financial`: troque as linhas 100-103 por `var receivable = orders.sumReceivable();` e remova o import `com.luthier.service.domain.entities.ServiceOrderEntity`, que fica sem uso.
- **MIRROR:** REPOSITORY_PATTERN (enum totalmente qualificado, `coalesce(sum(...), …)` como em `sumPaid`).
- **GOTCHA:**
  - O filtro `> 0` mantém a semântica atual (`filter(b -> b.signum() > 0)`), inclusive para dados legados com saldo negativo de antes do SRV-01.
  - Use aliases diferentes nas subqueries do `select` e do `where` se o parser reclamar de alias repetido (`i`/`p` no `select` e `i2`/`p2` no `where`).
  - **Fallback, se o Hibernate ou o banco recusarem subquery dentro do `sum`:** troque por `select <saldo> from ServiceOrderEntity o where <faturável> and <saldo> > 0` devolvendo `List<BigDecimal>` e some em Java. Ainda é 1 consulta sem entidades, e as linhas se limitam às OS **com saldo em aberto**, não ao histórico. Registre o desvio no relatório.
  - O custo no banco ainda percorre as OS faturáveis pelo índice `(tenant_id, status)`, com uma busca indexada por `order_id` em cada subquery. O que deixa de crescer é a memória, os objetos e as idas ao banco da aplicação. Isso atende ao critério; a materialização ficou fora (ver NOT Building).
- **VALIDATE:** `FinancialReceivableTest` verde (3 de 3). `ServiceOrderFlowTest` verde, porque ele também confere `/reports/financial`.

### Task 8: k6 e documentação
- **ACTION:**
  1. `load-tests/k6-luthieria.js`, grupo "balcão: consultas" (por volta da linha 92): acrescente `check(get('/reports/financial', t.token), { 'financeiro 200': (r) => r.status === 200 });`, no mesmo estilo da linha do dashboard. Esse arquivo pode ir na branch do SRV-03.
  2. `docs/auditoria.md`: acrescente ✔ ao título de SRV-02, SRV-03 e APP-04, cada um na sua branch, como a fase 3 fez (por exemplo, `### [MÉDIO] SRV-05: ... ✔`).
  3. Medição antes e depois (critério do PRD): com k6 instalado, suba `develop` (`java -jar srv-luthier-app/target/srv-luthier-app-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev`, perfil explícito por causa do APP-01), rode `k6 run -e PROFILE=smoke` e depois o perfil padrão, e anote o p95 de `/service-orders?status` e `/reports/financial`. Repita com as três branches combinadas. Registre os números no relatório da fase.
- **GOTCHA:** k6 e Docker **não estão instalados** nesta máquina (conferido no planejamento). O k6 tem binário próprio (`winget install k6`), mas instalar algo pede a aprovação do usuário. Sem k6, registre "não executado" no relatório, como foi feito com o `PostgresMigrationTest` na fase 3. A regressão fica garantida pelos testes de contagem.
- **VALIDATE:** `node --check load-tests/k6-luthieria.js` (só sintaxe; o node está instalado).

---

## Testing Strategy

### Unit / Integration Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `OrderListQueryCountTest.listingDoesNotLoadOrderItems` | 3 OS com serviço e peça, `GET /service-orders` | 0 coleções e 0 itens/peças carregados | Falha também com o APP-04 aplicado |
| `OrderListQueryCountTest.listingQueryCountDoesNotGrowWithPageSize` | página com 2 OS e depois com 12 | Mesmo `prepareStatementCount` | Ruído assíncrono (await) |
| `OrderListQueryCountTest.listingTotalsMatchDetail` | OS com desconto e OS sem itens | `totalAmount`/`balance` da listagem = os do detalhe; OS vazia = 0 | Sim (OS sem itens) |
| `FinancialReceivableTest.receivableSumsOpenBalancesOfBillableOrders` | OS A a F (tabela da Task 6) | `receivable = 410.00` | Saldo 0, status não faturável, outro tenant, entregue sem quitação |
| `FinancialReceivableTest.receivableDoesNotLoadOrders` | mesmo cenário | 0 `ServiceOrderEntity` carregadas | — |
| `FinancialReceivableTest.receivableQueryCountDoesNotGrowWithHistory` | +5 OS entregues quitadas | Mesmo `prepareStatementCount` | Crescimento do histórico |
| Suíte inteira (`verify`) | — | Verde com o APP-04 | Batch JDBC × SH-02 × PRO-01 |

### Edge Cases Checklist
- [x] Página vazia: `totalsOf` não é chamado (evita `in ()`)
- [x] OS sem itens: totais 0 pelo `getOrDefault` e pelo `coalesce`
- [x] Saldo legado negativo: excluído do "a receber" pelo `> 0`
- [x] Outro tenant: excluído pelo `@TenantId` no `o` externo
- [x] Concorrência e ruído de listeners assíncronos nas estatísticas: `await().untilAsserted`
- [ ] Permissão: inalterada (`/reports/**` e `/service-orders` mantêm o `@PreAuthorize`)

---

## Validation Commands

### Static Analysis
```bash
./mvnw -B -ntp -q compile
```
EXPECT: BUILD SUCCESS.

### Testes da fase
```bash
./mvnw -pl srv-luthier-app -am test -Dtest='OrderListQueryCountTest,FinancialReceivableTest,ServiceOrderFlowTest' -Dsurefire.failIfNoSpecifiedTests=false
```
EXPECT: todos verdes.

### Full Test Suite
```bash
./mvnw -B -ntp verify
```
EXPECT: verde, com o `ModularityTest` verde (nenhuma dependência nova entre módulos; `OrderTotals` é interno do service).

### Database Validation
Sem migration nesta fase. Com Docker, rode o `PostgresMigrationTest` para conferir que as JPQL novas também rodam no PostgreSQL. Ele só sobe o schema; para validar as consultas no PG, rode a suíte com o perfil do compose ou valide à mão no `docker compose up`.

### Manual Validation
- [ ] `java -jar ... --spring.profiles.active=dev`, crie 3 OS, abra `GET /service-orders` e `GET /reports/financial` no Swagger e confira os mesmos valores de antes.
- [ ] (Opcional) Com `logging.level.org.hibernate.SQL=debug`, confira que a listagem faz 3 SELECTs e o financeiro faz 1 SELECT para `receivable`.
- [ ] k6 antes e depois (Task 8), se o k6 estiver disponível.

---

## Acceptance Criteria
- [ ] SRV-02: a listagem faz um número constante de consultas (3) e não carrega `services` nem `parts`.
- [ ] SRV-03: `receivable` é calculado por uma agregação JPQL (sem SQL nativo), com o mesmo valor do cálculo atual e sem carregar entidades.
- [ ] APP-04: as quatro propriedades estão em `application.yaml` e a suíte inteira está verde.
- [ ] k6 antes e depois registrado no relatório, ou "não executado" com o motivo.
- [ ] `docs/auditoria.md` com os três achados marcados.

## Completion Checklist
- [ ] Código segue os padrões descobertos (text block JPQL, records em `domain/records`)
- [ ] Nenhum log novo no módulo service
- [ ] Testes no estilo `*Test` do `srv-luthier-app`, com `ApiClient`
- [ ] Sem valores mágicos (batch 50 vem da sugestão da auditoria, comentado no YAML)
- [ ] Sem mudança de contrato da API
- [ ] Sem escopo extra (sem materialização, sem SRV-10)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Subquery dentro de `sum` recusada pelo Hibernate 7.4 ou pelo PostgreSQL | Baixa (H2 conferido; PG aceita) | Médio | Fallback da Task 7 (lista de saldos em aberto somada em Java) |
| `0bd` ou a projeção por construtor recusados | Baixa | Baixo | `cast(0 as BigDecimal)` ou `Object[]` (Task 4) |
| Testes de contagem instáveis por causa de listeners assíncronos (estatísticas globais) | Média | Baixo | `await().untilAsserted` e asserts por nome de entidade do módulo service |
| Batch JDBC muda o SQLState visto pelo handler do SH-02 (22001 vira 409) | Baixa | Baixo | Rodar `GlobalExceptionHandlerTest` e `RegistrationFlowTest` depois do rebase sobre a fase 3 |
| Conflito com a fase 3 no `application.yaml` | Média | Baixo | Hunks diferentes; rebase simples |
| JPQL nova nunca rodou no PostgreSQL (sem Docker) | Alta | Médio | Mesmo risco já registrado no PRD; rodar `docker compose up` e o k6 quando houver Docker |

## Notes
- O APP-04 sozinho já deixaria o número de consultas da listagem constante (batch de 50 para uma página de 20). O SRV-02 vai além: deixa de trazer as linhas de item e peça, que a listagem não exibe. Por isso o teste do SRV-02 mede carga de coleção, e não só o número de consultas.
- Confiança na primeira passada: 8/10. A incerteza está no parser HQL para `0bd` e para subquery dentro do `sum`, e as duas coisas têm fallback descrito.
