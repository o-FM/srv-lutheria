package com.luthier;

import static com.luthier.support.ApiClient.auth;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.luthier.support.ApiClient;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ciclo completo da OS: abertura → orçamento → aprovação pelo portal → execução → pronto (baixa
 * de estoque assíncrona via evento) → pagamento → entrega → relatórios.
 */
@SpringBootTest(properties = {
    "app.security.rate-limit.login.capacity=1000",
    "app.security.rate-limit.portal.capacity=1000"})
@AutoConfigureMockMvc
class ServiceOrderFlowTest {

  @Autowired
  MockMvc mvc;

  ApiClient api;
  String admin;
  String customerId;
  String instrumentId;
  String productId;
  String catalogId;

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

  @Test
  void fullLifecycle() throws Exception {
    // 1. Abertura com serviço do catálogo
    var orderId = api.create(admin, "/service-orders", """
        {"customerId":"%s","instrumentId":"%s","priority":"alta",
         "reportedIssue":"Trastejando na 5ª casa","estimatedDeliveryDate":"2030-01-10",
         "services":[{"catalogId":"%s"}]}""".formatted(customerId, instrumentId, catalogId));
    mvc.perform(auth(get("/service-orders/" + orderId), admin))
        .andExpect(jsonPath("$.status").value("recebido"))
        .andExpect(jsonPath("$.orderNumber").value(1))
        .andExpect(jsonPath("$.services[0].laborPrice").value(180.00));

    // 2. Diagnóstico, peça e orçamento (2 x 45 + 180 - 20 = 250)
    moveTo(orderId, "diagnostico").andExpect(status().isOk());
    moveTo(orderId, "orcamento").andExpect(status().isOk());
    mvc.perform(auth(post("/service-orders/" + orderId + "/parts"), admin)
            .content("{\"productId\":\"%s\",\"quantity\":2}".formatted(productId)))
        .andExpect(status().isCreated());
    mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .patch("/service-orders/" + orderId), admin).content("{\"discount\":20}"))
        .andExpect(jsonPath("$.totalAmount").value(250.00));
    var sent = mvc.perform(auth(post("/service-orders/" + orderId + "/quote/send"), admin))
        .andExpect(jsonPath("$.status").value("aguardando_aprovacao"))
        .andExpect(jsonPath("$.quote.status").value("pendente"))
        .andReturn().getResponse().getContentAsString();
    String token = JsonPath.read(sent, "$.clientTrackingToken");

    // Itens travados após o envio do orçamento
    mvc.perform(auth(post("/service-orders/" + orderId + "/parts"), admin)
            .content("{\"productId\":\"%s\",\"quantity\":1}".formatted(productId)))
        .andExpect(status().isConflict());

    // 3. Portal do cliente (sem login): vê só o primeiro nome e aprova
    mvc.perform(get("/portal/orders/" + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerFirstName").value("Lucas"))
        .andExpect(jsonPath("$.instrument").value("Fender Stratocaster"))
        .andExpect(jsonPath("$.quote.grandTotal").value(250.00));
    mvc.perform(post("/portal/orders/" + token + "/quote/approve"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("em_execucao"));

    // 4. Pronto: baixa assíncrona do estoque (3 - 2 = 1, abaixo do mínimo)
    moveTo(orderId, "pronto").andExpect(status().isOk());
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        mvc.perform(auth(get("/products/" + productId), admin))
            .andExpect(jsonPath("$.currentQuantity").value(1))
            .andExpect(jsonPath("$.lowStock").value(true)));
    mvc.perform(auth(get("/products/" + productId + "/movements"), admin))
        .andExpect(jsonPath("$.content[0].reason").value("OS #1"))
        .andExpect(jsonPath("$.content[0].orderId").value(orderId));

    // 5. Pagamento (não pode passar do saldo) e entrega
    payment(orderId, "300.00").andExpect(status().isConflict());
    payment(orderId, "250.00").andExpect(status().isCreated())
        .andExpect(jsonPath("$.balance").value(0));
    moveTo(orderId, "entregue").andExpect(status().isOk());

    mvc.perform(auth(get("/service-orders/" + orderId), admin))
        .andExpect(jsonPath("$.timeline", hasSize(8)))
        .andExpect(jsonPath("$.payments[0].receiptNumber").value("REC-1-01"));

    // 6. Relatórios
    mvc.perform(auth(get("/reports/dashboard"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ordersByStatus.entregue").value(1))
        .andExpect(jsonPath("$.lowStockItems").value(1))
        .andExpect(jsonPath("$.activeCustomers").value(1));
    mvc.perform(auth(get("/reports/financial"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.received").value(250.00))
        .andExpect(jsonPath("$.byMethod.pix.count").value(1))
        .andExpect(jsonPath("$.deliveredOrders").value(1));
  }

  @Test
  void invalidTransitionsAreRejected() throws Exception {
    var orderId = newOrder();
    moveTo(orderId, "entregue").andExpect(status().isConflict());
    // Sem itens não há orçamento
    moveTo(orderId, "orcamento").andExpect(status().isOk());
    mvc.perform(auth(post("/service-orders/" + orderId + "/quote/send"), admin))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectedQuoteGoesBackToBudget() throws Exception {
    var orderId = newOrder();
    moveTo(orderId, "orcamento").andExpect(status().isOk());
    mvc.perform(auth(post("/service-orders/" + orderId + "/services"), admin)
            .content("{\"title\":\"Troca de trastes\",\"laborPrice\":900}"))
        .andExpect(status().isCreated());
    mvc.perform(auth(post("/service-orders/" + orderId + "/quote/send"), admin));
    mvc.perform(auth(post("/service-orders/" + orderId + "/quote/reject"), admin)
            .content("{\"reason\":\"Muito caro\"}"))
        .andExpect(jsonPath("$.status").value("orcamento"))
        .andExpect(jsonPath("$.quote.status").value("rejeitado"))
        .andExpect(jsonPath("$.quote.rejectionReason").value("Muito caro"));
  }

  @Test
  void onlyBenchCanFinishAndStockIsCheckedBeforeFinishing() throws Exception {
    var attendant = api.createUser(admin, "ATENDENTE");
    var orderId = newOrder();
    moveTo(orderId, "orcamento");
    mvc.perform(auth(post("/service-orders/" + orderId + "/parts"), admin)
        .content("{\"productId\":\"%s\",\"quantity\":3}".formatted(productId)));
    mvc.perform(auth(post("/service-orders/" + orderId + "/quote/send"), admin));
    mvc.perform(auth(post("/service-orders/" + orderId + "/quote/approve"), attendant))
        .andExpect(status().isOk());

    // Atendente não conclui OS
    mvc.perform(auth(post("/service-orders/" + orderId + "/status"), attendant)
            .content("{\"status\":\"pronto\"}"))
        .andExpect(status().isForbidden());

    // Estoque consumido por fora: conclusão é barrada antes de virar baixa assíncrona com erro
    mvc.perform(auth(post("/products/" + productId + "/movements"), admin)
        .content("{\"type\":\"saida\",\"quantity\":2,\"reason\":\"venda balcão\"}"));
    moveTo(orderId, "pronto").andExpect(status().isConflict());
  }

  @Test
  void ordersAndPortalRespectTenants() throws Exception {
    var orderId = newOrder();
    var other = api.registerWorkshop("Outra oficina");

    mvc.perform(auth(get("/service-orders/" + orderId), other)).andExpect(status().isNotFound());
    mvc.perform(auth(get("/service-orders"), other)).andExpect(jsonPath("$.totalElements").value(0));
    // Numeração é por luthieria: a primeira OS da outra também é #1
    var otherCustomer = api.create(other, "/customers", "{\"name\":\"Ana\",\"phone\":\"11999998888\"}");
    var otherInstrument = api.create(other, "/customers/" + otherCustomer + "/instruments",
        "{\"type\":\"baixo\",\"brand\":\"Ibanez\",\"model\":\"SR300\"}");
    api.create(other, "/service-orders", """
        {"customerId":"%s","instrumentId":"%s","reportedIssue":"Chiado"}"""
        .formatted(otherCustomer, otherInstrument), "$.orderNumber");
    mvc.perform(auth(get("/service-orders?number=1&full=true"), other))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].timeline.length()").value(1));
    mvc.perform(auth(get("/instruments"), other))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].brand").value("Ibanez"));

    // Instrumento de outra luthieria não pode ser usado
    mvc.perform(auth(post("/service-orders"), other).content("""
            {"customerId":"%s","instrumentId":"%s","reportedIssue":"x"}"""
            .formatted(customerId, instrumentId)))
        .andExpect(status().isNotFound());

    mvc.perform(get("/portal/orders/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/portal/orders/curto")).andExpect(status().isBadRequest());
  }

  private String newOrder() throws Exception {
    return api.create(admin, "/service-orders", """
        {"customerId":"%s","instrumentId":"%s","reportedIssue":"Revisão geral"}"""
        .formatted(customerId, instrumentId));
  }

  private org.springframework.test.web.servlet.ResultActions moveTo(String orderId, String to)
      throws Exception {
    return mvc.perform(auth(post("/service-orders/" + orderId + "/status"), admin)
        .content("{\"status\":\"%s\"}".formatted(to)));
  }

  private org.springframework.test.web.servlet.ResultActions payment(String orderId,
      String amount) throws Exception {
    return mvc.perform(auth(post("/service-orders/" + orderId + "/payments"), admin)
        .content("{\"amount\":%s,\"method\":\"pix\"}".formatted(amount)));
  }
}
