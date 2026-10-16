package com.luthier;

import static com.luthier.support.ApiClient.auth;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    String id = com.jayway.jsonpath.JsonPath.read(pending, "$.content[0].id");
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

  @Test
  void metricsAreExposed() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("jvm_memory_used_bytes")))
        .andExpect(content().string(containsString("luthieria_notifications_total")));
  }
}
