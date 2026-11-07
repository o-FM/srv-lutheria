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
import java.util.List;
import java.util.Map;
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
 * SRV-02: a listagem de OS (kanban) calcula os totais no banco, sem carregar os itens e as peças
 * de cada OS, e o número de consultas não cresce com o tamanho da página.
 */
@SpringBootTest(properties = {
    "app.security.rate-limit.login.capacity=1000",
    "app.security.rate-limit.portal.capacity=1000",
    "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
class OrderListQueryCountTest {

  private static final String ITEM_ENTITY =
      "com.luthier.service.domain.entities.OrderServiceItemEntity";
  private static final String PART_ENTITY = "com.luthier.service.domain.entities.OrderPartEntity";

  @Autowired
  MockMvc mvc;

  @Autowired
  EntityManagerFactory emf;

  Statistics stats;
  ApiClient api;
  String admin;
  String customerId;
  String instrumentId;
  String productId;
  String catalogId;

  @BeforeEach
  void setUp() throws Exception {
    stats = emf.unwrap(SessionFactory.class).getStatistics();
    api = new ApiClient(mvc);
    admin = api.registerWorkshop("Oficina Listagem");
    customerId = api.create(admin, "/customers",
        "{\"name\":\"Lucas Silveira\",\"phone\":\"(11) 98765-4321\"}");
    instrumentId = api.create(admin, "/customers/" + customerId + "/instruments",
        "{\"type\":\"guitarra\",\"brand\":\"Fender\",\"model\":\"Stratocaster\"}");
    productId = api.create(admin, "/products", """
        {"name":"Encordoamento 0.10","category":"cordas","sku":"EXL110","unit":"jogo",
         "costPrice":25,"salePrice":45,"minQuantity":2,"initialQuantity":100}""");
    catalogId = api.create(admin, "/service-catalog",
        "{\"title\":\"Regulagem completa\",\"laborPrice\":180.00,\"estimatedDays\":3}");
  }

  @Test
  void listingDoesNotLoadOrderItems() throws Exception {
    for (int i = 0; i < 3; i++) {
      orderWithItems();
    }

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      measureListing();
      assertThat(stats.getEntityStatistics(ITEM_ENTITY).getLoadCount()).isZero();
      assertThat(stats.getEntityStatistics(PART_ENTITY).getLoadCount()).isZero();
      assertThat(stats.getCollectionFetchCount()).isZero();
    });
  }

  @Test
  void listingQueryCountDoesNotGrowWithPageSize() throws Exception {
    orderWithItems();
    orderWithItems();
    var smallPage = stableStatementCount();

    for (int i = 0; i < 10; i++) {
      orderWithItems();
    }
    var bigPage = stableStatementCount();

    assertThat(bigPage).isEqualTo(smallPage);
  }

  @Test
  void listingTotalsMatchDetail() throws Exception {
    orderWithItems();
    orderWithItems();
    var emptyOrder = openOrder(false);

    var body = mvc.perform(auth(get("/service-orders?size=20"), admin))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    List<Map<String, Object>> content = JsonPath.read(body, "$.content");
    assertThat(content).hasSize(3);

    for (var summary : content) {
      var id = summary.get("id").toString();
      var detail = mvc.perform(auth(get("/service-orders/" + id), admin))
          .andReturn().getResponse().getContentAsString();
      var expectedTotal = id.equals(emptyOrder) ? BigDecimal.ZERO : new BigDecimal("215.00");
      assertThat(decimal(summary.get("totalAmount"))).isEqualByComparingTo(expectedTotal)
          .isEqualByComparingTo(decimal(JsonPath.read(detail, "$.totalAmount")));
      assertThat(decimal(summary.get("balance")))
          .isEqualByComparingTo(decimal(JsonPath.read(detail, "$.balance")));
    }
  }

  // ---------------- apoio ----------------

  /** OS em orçamento com serviço (180) + peça (45) - desconto (10) = 215, sem pagamento. */
  private String orderWithItems() throws Exception {
    var orderId = openOrder(true);
    moveTo(orderId, "diagnostico");
    moveTo(orderId, "orcamento");
    mvc.perform(auth(post("/service-orders/" + orderId + "/parts"), admin)
            .content("{\"productId\":\"%s\",\"quantity\":1}".formatted(productId)))
        .andExpect(status().isCreated());
    mvc.perform(auth(patch("/service-orders/" + orderId), admin).content("{\"discount\":10}"))
        .andExpect(status().isOk());
    return orderId;
  }

  private String openOrder(boolean withService) throws Exception {
    var services = withService ? ",\"services\":[{\"catalogId\":\"%s\"}]".formatted(catalogId) : "";
    return api.create(admin, "/service-orders", """
        {"customerId":"%s","instrumentId":"%s","reportedIssue":"Trastejando"%s}"""
        .formatted(customerId, instrumentId, services));
  }

  private void moveTo(String orderId, String to) throws Exception {
    mvc.perform(auth(post("/service-orders/" + orderId + "/status"), admin)
            .content("{\"status\":\"%s\"}".formatted(to)))
        .andExpect(status().isOk());
  }

  private void measureListing() throws Exception {
    stats.clear();
    mvc.perform(auth(get("/service-orders?size=20"), admin)).andExpect(status().isOk());
  }

  /**
   * As estatísticas são globais: listeners assíncronos (notificações das transições) podem somar
   * consultas durante a medição. Mede até duas leituras seguidas darem o mesmo número.
   */
  private long stableStatementCount() {
    var previous = new AtomicLong(-1);
    var stable = new AtomicLong();
    await().atMost(Duration.ofSeconds(10)).until(() -> {
      measureListing();
      var count = stats.getPrepareStatementCount();
      stable.set(count);
      return previous.getAndSet(count) == count;
    });
    return stable.get();
  }

  private static BigDecimal decimal(Object value) {
    return new BigDecimal(value.toString());
  }
}
