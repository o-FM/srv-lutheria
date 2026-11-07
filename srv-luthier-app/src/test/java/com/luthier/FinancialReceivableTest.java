package com.luthier;

import static com.luthier.support.ApiClient.auth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.luthier.support.ApiClient;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SRV-03: o "a receber" do financeiro é somado no banco, sem carregar as OS, com o mesmo valor do
 * cálculo antigo (saldos positivos das OS em execução, prontas ou entregues do próprio tenant).
 */
@SpringBootTest(properties = {
    "app.security.rate-limit.login.capacity=1000",
    "app.security.rate-limit.portal.capacity=1000",
    "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
class FinancialReceivableTest {

  private static final String ORDER_ENTITY =
      "com.luthier.service.domain.entities.ServiceOrderEntity";

  @Autowired
  MockMvc mvc;

  @Autowired
  EntityManagerFactory emf;

  Statistics stats;
  ApiClient api;
  Workshop main;

  @BeforeEach
  void setUp() throws Exception {
    stats = emf.unwrap(SessionFactory.class).getStatistics();
    api = new ApiClient(mvc);
    main = workshop("Oficina Financeiro");

    // A: em execução, 180 + 45 - 25 = 200, pago 50 → saldo 150
    var a = approvedOrder(main, true, "25");
    payment(main, a, "50");
    // B: pronta, 180, sem pagamento → 180
    var b = approvedOrder(main, false, null);
    moveTo(main, b, "pronto");
    // C: entregue e quitada → 0 (fora)
    deliveredOrder(main, "180");
    // D: entregue com 100 pagos (entrega não exige quitação) → 80
    deliveredOrder(main, "100");
    // E: ainda em orçamento → status não faturável (fora)
    var e = openOrder(main);
    moveTo(main, e, "diagnostico");
    moveTo(main, e, "orcamento");
    // F: outra luthieria, em execução, 180 → outro tenant (fora)
    var other = workshop("Outra Oficina");
    approvedOrder(other, false, null);
  }

  @Test
  void receivableSumsOpenBalancesOfBillableOrders() throws Exception {
    assertThat(receivable()).isEqualByComparingTo("410.00");
  }

  @Test
  void receivableDoesNotLoadOrders() {
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      stats.clear();
      receivable();
      assertThat(stats.getEntityStatistics(ORDER_ENTITY).getLoadCount()).isZero();
    });
  }

  @Test
  void receivableQueryCountDoesNotGrowWithHistory() throws Exception {
    var before = stableStatementCount();
    for (int i = 0; i < 5; i++) {
      deliveredOrder(main, "180");
    }
    var after = stableStatementCount();

    assertThat(after).isEqualTo(before);
    assertThat(receivable()).isEqualByComparingTo("410.00");
  }

  // ---------------- apoio ----------------

  record Workshop(String token, String customerId, String instrumentId, String productId,
                  String catalogId) {}

  private Workshop workshop(String name) throws Exception {
    var token = api.registerWorkshop(name);
    var customerId = api.create(token, "/customers",
        "{\"name\":\"Lucas Silveira\",\"phone\":\"(11) 98765-4321\"}");
    var instrumentId = api.create(token, "/customers/" + customerId + "/instruments",
        "{\"type\":\"guitarra\",\"brand\":\"Fender\",\"model\":\"Stratocaster\"}");
    var productId = api.create(token, "/products", """
        {"name":"Encordoamento 0.10","category":"cordas","sku":"EXL110","unit":"jogo",
         "costPrice":25,"salePrice":45,"minQuantity":2,"initialQuantity":100}""");
    var catalogId = api.create(token, "/service-catalog",
        "{\"title\":\"Regulagem completa\",\"laborPrice\":180.00,\"estimatedDays\":3}");
    return new Workshop(token, customerId, instrumentId, productId, catalogId);
  }

  private String openOrder(Workshop w) throws Exception {
    return api.create(w.token(), "/service-orders", """
        {"customerId":"%s","instrumentId":"%s","reportedIssue":"Trastejando",
         "services":[{"catalogId":"%s"}]}""".formatted(w.customerId(), w.instrumentId(),
        w.catalogId()));
  }

  /** OS com serviço de 180 (mais peça de 45 e desconto, se pedidos), aprovada pelo portal. */
  private String approvedOrder(Workshop w, boolean withPart, String discount) throws Exception {
    var orderId = openOrder(w);
    moveTo(w, orderId, "diagnostico");
    moveTo(w, orderId, "orcamento");
    if (withPart) {
      mvc.perform(auth(post("/service-orders/" + orderId + "/parts"), w.token())
              .content("{\"productId\":\"%s\",\"quantity\":1}".formatted(w.productId())))
          .andExpect(status().isCreated());
    }
    if (discount != null) {
      mvc.perform(auth(patch("/service-orders/" + orderId), w.token())
              .content("{\"discount\":%s}".formatted(discount)))
          .andExpect(status().isOk());
    }
    var sent = mvc.perform(auth(post("/service-orders/" + orderId + "/quote/send"), w.token()))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String token = JsonPath.read(sent, "$.clientTrackingToken");
    mvc.perform(post("/portal/orders/" + token + "/quote/approve"))
        .andExpect(status().isOk());
    return orderId;
  }

  /** OS de 180 entregue com o valor pago informado. */
  private void deliveredOrder(Workshop w, String paid) throws Exception {
    var orderId = approvedOrder(w, false, null);
    payment(w, orderId, paid);
    moveTo(w, orderId, "pronto");
    moveTo(w, orderId, "entregue");
  }

  private void moveTo(Workshop w, String orderId, String to) throws Exception {
    mvc.perform(auth(post("/service-orders/" + orderId + "/status"), w.token())
            .content("{\"status\":\"%s\"}".formatted(to)))
        .andExpect(status().isOk());
  }

  private void payment(Workshop w, String orderId, String amount) throws Exception {
    mvc.perform(auth(post("/service-orders/" + orderId + "/payments"), w.token())
            .content("{\"amount\":%s,\"method\":\"pix\"}".formatted(amount)))
        .andExpect(status().isCreated());
  }

  private BigDecimal receivable() throws Exception {
    var body = mvc.perform(auth(get("/reports/financial"), main.token()))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    return new BigDecimal(JsonPath.read(body, "$.receivable").toString());
  }

  /**
   * As estatísticas são globais: listeners assíncronos (notificações das transições) podem somar
   * consultas durante a medição. Mede até duas leituras seguidas darem o mesmo número.
   */
  private long stableStatementCount() {
    var previous = new AtomicLong(-1);
    var stable = new AtomicLong();
    await().atMost(Duration.ofSeconds(10)).until(() -> {
      stats.clear();
      receivable();
      var count = stats.getPrepareStatementCount();
      stable.set(count);
      return previous.getAndSet(count) == count;
    });
    return stable.get();
  }
}
