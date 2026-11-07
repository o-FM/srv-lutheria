package com.luthier;

import static com.luthier.support.ApiClient.auth;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.luthier.support.ApiClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Notificações disparadas por eventos de outros módulos + métricas expostas no actuator. */
@SpringBootTest(properties = "app.security.rate-limit.login.capacity=1000")
@AutoConfigureMockMvc
@AutoConfigureMetrics
class NotificationFlowTest {

  @Autowired
  MockMvc mvc;

  @Test
  void quoteSentNotifiesCustomerByEmailAndWhatsapp() throws Exception {
    var api = new ApiClient(mvc);
    var admin = api.registerWorkshop("Oficina Notifica");

    // Boas-vindas ao dono (sem SMTP em teste: fica registrada como falha de envio)
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        mvc.perform(auth(get("/notifications?channel=email"), admin))
            .andExpect(jsonPath("$.content[*].subject", hasItem("Bem-vindo(a) à Luthieria!"))));

    var customer = api.create(admin, "/customers",
        "{\"name\":\"Lucas Silveira\",\"phone\":\"(11) 98765-4321\",\"email\":\"lucas@teste.com\"}");
    var instrument = api.create(admin, "/customers/" + customer + "/instruments",
        "{\"type\":\"violino\",\"brand\":\"Eagle\",\"model\":\"VE441\"}");
    var order = api.create(admin, "/service-orders", """
        {"customerId":"%s","instrumentId":"%s","reportedIssue":"Alma caída",
         "services":[{"title":"Ajuste de alma","laborPrice":120}]}"""
        .formatted(customer, instrument));
    mvc.perform(auth(post("/service-orders/" + order + "/status"), admin)
        .content("{\"status\":\"orcamento\"}"));
    mvc.perform(auth(post("/service-orders/" + order + "/quote/send"), admin))
        .andExpect(status().isOk());

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        mvc.perform(auth(get("/notifications?channel=whatsapp&status=pendente"), admin))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].actionUrl", startsWith("https://wa.me/5511987654321?text=")))
            .andExpect(jsonPath("$.content[0].body", containsString("120,00")))
            .andExpect(jsonPath("$.content[0].body", containsString("/acompanhar/"))));
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        mvc.perform(auth(get("/notifications?channel=email"), admin))
            .andExpect(jsonPath("$.content[*].recipient", hasItem("lucas@teste.com"))));

    // Balcão envia o WhatsApp e marca como enviado
    var pending = mvc.perform(auth(get("/notifications?channel=whatsapp"), admin))
        .andReturn().getResponse().getContentAsString();
    String id = JsonPath.read(pending, "$.content[0].id");
    mvc.perform(auth(post("/notifications/" + id + "/sent"), admin))
        .andExpect(jsonPath("$.status").value("enviado"));

    // Outra luthieria não vê as notificações
    var other = api.registerWorkshop("Outra");
    mvc.perform(auth(get("/notifications?channel=whatsapp"), other))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void lowStockCreatesInternalAlert() throws Exception {
    var api = new ApiClient(mvc);
    var admin = api.registerWorkshop("Oficina Estoque");
    var product = api.create(admin, "/products", """
        {"name":"Tarraxa Grover","category":"pecas","sku":"GRV-1","unit":"jogo",
         "costPrice":100,"salePrice":180,"minQuantity":2,"initialQuantity":3}""");
    mvc.perform(auth(post("/products/" + product + "/movements"), admin)
        .content("{\"type\":\"saida\",\"quantity\":1,\"reason\":\"uso\"}"));

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        mvc.perform(auth(get("/notifications?channel=interno"), admin))
            .andExpect(jsonPath("$.content[0].subject").value("Estoque baixo: Tarraxa Grover")));
  }

  /**
   * NOT-01: o estoquista não tem acesso a clientes nem a OS, então não pode ver contatos nem o
   * link do portal (que carrega o token da OS e permite responder o orçamento). Ele só vê e lê
   * os alertas internos (estoque baixo).
   */
  @Test
  void stockClerkSeesOnlyInternalAlerts() throws Exception {
    var api = new ApiClient(mvc);
    var admin = api.registerWorkshop("Oficina Permissões");
    var stockClerk = api.createUser(admin, "ESTOQUISTA");
    sendQuoteAndTriggerLowStock(api, admin);

    // O estoquista só enxerga os alertas internos, mesmo sem filtrar por canal
    mvc.perform(auth(get("/notifications"), stockClerk))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[*].channel", everyItem(equalTo("interno"))));
    mvc.perform(auth(get("/notifications?channel=interno"), stockClerk))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(auth(get("/notifications?channel=whatsapp"), stockClerk))
        .andExpect(status().isForbidden());
    mvc.perform(auth(get("/notifications?channel=email"), stockClerk))
        .andExpect(status().isForbidden());

    // Mensagem ao cliente "não existe" para o estoquista (404); o alerta ele marca como lido
    mvc.perform(auth(post("/notifications/" + firstId(admin, "whatsapp") + "/sent"), stockClerk))
        .andExpect(status().isNotFound());
    mvc.perform(auth(post("/notifications/" + firstId(admin, "interno") + "/sent"), stockClerk))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("enviado"));
  }

  /** NOT-01: o balcão continua enviando o WhatsApp em 1 toque (link wa.me com o portal). */
  @Test
  void deskStillSendsWhatsappWithPortalLink() throws Exception {
    var api = new ApiClient(mvc);
    var admin = api.registerWorkshop("Oficina Balcão");
    var attendant = api.createUser(admin, "ATENDENTE");
    sendQuoteAndTriggerLowStock(api, admin);

    mvc.perform(auth(get("/notifications?channel=whatsapp"), attendant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].actionUrl", startsWith("https://wa.me/55")))
        .andExpect(jsonPath("$.content[0].body", containsString("/acompanhar/")));
    mvc.perform(auth(get("/notifications"), attendant))
        .andExpect(jsonPath("$.content[*].channel", hasItem("email")))
        .andExpect(jsonPath("$.content[*].channel", hasItem("interno")));
    mvc.perform(auth(post("/notifications/" + firstId(admin, "whatsapp") + "/sent"), attendant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("enviado"));
  }

  /** Gera, na luthieria do admin, e-mail + WhatsApp para o cliente e um alerta interno. */
  private void sendQuoteAndTriggerLowStock(ApiClient api, String admin) throws Exception {
    var customer = api.create(admin, "/customers",
        "{\"name\":\"Ana Costa\",\"phone\":\"(11) 91234-5678\",\"email\":\"ana@teste.com\"}");
    var instrument = api.create(admin, "/customers/" + customer + "/instruments",
        "{\"type\":\"violino\",\"brand\":\"Eagle\",\"model\":\"VE441\"}");
    var order = api.create(admin, "/service-orders", """
        {"customerId":"%s","instrumentId":"%s","reportedIssue":"Cavalete torto",
         "services":[{"title":"Ajuste de cavalete","laborPrice":90}]}"""
        .formatted(customer, instrument));
    mvc.perform(auth(post("/service-orders/" + order + "/status"), admin)
        .content("{\"status\":\"orcamento\"}"));
    mvc.perform(auth(post("/service-orders/" + order + "/quote/send"), admin))
        .andExpect(status().isOk());

    var product = api.create(admin, "/products", """
        {"name":"Cavalete de violino","category":"pecas","sku":"CAV-%d","unit":"un",
         "costPrice":20,"salePrice":45,"minQuantity":2,"initialQuantity":3}"""
        .formatted(System.nanoTime()));
    mvc.perform(auth(post("/products/" + product + "/movements"), admin)
        .content("{\"type\":\"saida\",\"quantity\":1,\"reason\":\"uso\"}"));

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      // E-mail: boas-vindas ao dono + orçamento ao cliente
      mvc.perform(auth(get("/notifications?channel=email"), admin))
          .andExpect(jsonPath("$.totalElements").value(2));
      mvc.perform(auth(get("/notifications?channel=whatsapp"), admin))
          .andExpect(jsonPath("$.totalElements").value(1));
      mvc.perform(auth(get("/notifications?channel=interno"), admin))
          .andExpect(jsonPath("$.totalElements").value(1));
    });
  }

  private String firstId(String token, String channel) throws Exception {
    var body = mvc.perform(auth(get("/notifications?channel=" + channel), token))
        .andReturn().getResponse().getContentAsString();
    return JsonPath.read(body, "$.content[0].id");
  }

  @Test
  void metricsAreExposed() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("jvm_memory_used_bytes")))
        .andExpect(content().string(containsString("luthieria_notifications_total")));
  }
}
