package com.luthier;

import static com.luthier.support.ApiClient.auth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.luthier.notification.services.WhatsAppSender;
import com.luthier.support.ApiClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Área do cliente: OS aberta → WhatsApp automático com link + código amigável (XXXX-XXXX) →
 * cliente entra só com o código e vê apenas as próprias OS.
 */
@SpringBootTest(properties = "app.security.rate-limit.login.capacity=1000")
@AutoConfigureMockMvc
@Import(CustomerAreaTest.FakeWhatsApp.class)
class CustomerAreaTest {

  record Sent(String phone, String template, List<String> params, String button) {}

  /** WhatsApp de teste: guarda o que seria enviado à Meta. */
  @TestConfiguration(proxyBeanMethods = false)
  static class FakeWhatsApp {
    static final List<Sent> SENT = new CopyOnWriteArrayList<>();

    @Bean
    @Primary
    WhatsAppSender fakeWhatsAppSender() {
      return new WhatsAppSender() {
        public boolean isEnabled() { return true; }
        public void sendTemplate(String phone, String template, List<String> params, String button) {
          SENT.add(new Sent(phone, template, params, button));
        }
      };
    }
  }

  @Autowired
  MockMvc mvc;

  ApiClient api;
  String admin;
  String phone;
  String customerId;
  String instrumentId;
  String orderId;

  @BeforeEach
  void openOrder() throws Exception {
    api = new ApiClient(mvc);
    admin = api.registerWorkshop("Oficina Cliente " + System.nanoTime());
    phone = "119%08d".formatted(Math.floorMod(System.nanoTime(), 100_000_000L));
    customerId = api.create(admin, "/customers",
        "{\"name\":\"Lucas Silveira\",\"phone\":\"%s\"}".formatted(phone));
    instrumentId = api.create(admin, "/customers/" + customerId + "/instruments",
        "{\"type\":\"guitarra\",\"brand\":\"Fender\",\"model\":\"Strat\"}");
    orderId = openOrder(instrumentId);
  }

  @Test
  void openingAnOrderSendsLinkAndCodeAndCustomerSeesOnlyOwnOrders() throws Exception {
    // WhatsApp automático: mensagem de OS aberta (com o link) + código amigável
    var code = awaitCode(null);
    assertThat(code).matches("[2-9A-HJKMNP-TV-Z]{4}-[2-9A-HJKMNP-TV-Z]{4}");
    var opened = FakeWhatsApp.SENT.stream()
        .filter(s -> s.phone().equals(phone) && s.template().equals("luthieria_os_aberta"))
        .findFirst().orElseThrow();
    assertThat(opened.params().get(3)).endsWith("/cliente");

    // Histórico da oficina não guarda o código
    mvc.perform(auth(get("/notifications?channel=whatsapp"), admin))
        .andExpect(jsonPath("$.content[?(@.subject == 'Código de acesso')].body")
            .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString(code)))));

    // Só o código: sem telefone nem oficina; aceita minúsculas e sem traço
    var client = login(code.replace("-", "").toLowerCase());
    mvc.perform(auth(get("/client/orders"), client))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(orderId))
        .andExpect(jsonPath("$[0].instrument").value("Fender Strat"));

    // Aprova o orçamento pelo app
    mvc.perform(auth(post("/service-orders/" + orderId + "/status"), admin).content("{\"status\":\"orcamento\"}"));
    mvc.perform(auth(post("/service-orders/" + orderId + "/quote/send"), admin));
    mvc.perform(auth(post("/client/orders/" + orderId + "/quote/approve"), client))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("em_execucao"));

    // Token de cliente não acessa nada do balcão
    mvc.perform(auth(get("/customers"), client)).andExpect(status().isForbidden());
    mvc.perform(auth(get("/service-orders/" + orderId), client)).andExpect(status().isForbidden());
    mvc.perform(auth(post("/customers/" + customerId + "/access-code"), client))
        .andExpect(status().isForbidden());

    // Código é reutilizável
    assertThat(login(code)).isNotBlank();
  }

  @Test
  void newOrderOrDeskResendRotatesTheCode() throws Exception {
    var first = awaitCode(null);
    loginExpecting("ZZZZ-ZZZZ".equals(first) ? "YYYY-YYYY" : "ZZZZ-ZZZZ", 401);
    loginExpecting("abc", 401);

    // Nova OS → novo código; o anterior deixa de valer
    openOrder(instrumentId);
    var second = awaitCode(first);
    loginExpecting(first, 401);
    var client = login(second);
    mvc.perform(auth(get("/client/orders"), client)).andExpect(jsonPath("$.length()").value(2));

    // Balcão: "Enviar código de acesso"
    mvc.perform(auth(post("/customers/" + customerId + "/access-code"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("enviado"))
        .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.not(
            org.hamcrest.Matchers.containsString(second))));
    var third = awaitCode(second);
    loginExpecting(second, 401);
    assertThat(login(third)).isNotBlank();

    // Cliente de outra oficina não existe aqui
    var otherAdmin = api.registerWorkshop("Outra Oficina " + System.nanoTime());
    mvc.perform(auth(post("/customers/" + customerId + "/access-code"), otherAdmin))
        .andExpect(status().isNotFound());
  }

  // ---- helpers ----

  private String openOrder(String instrument) throws Exception {
    return api.create(admin, "/service-orders", """
        {"customerId":"%s","instrumentId":"%s","reportedIssue":"Trastejando",
         "services":[{"title":"Regulagem","laborPrice":180}]}""".formatted(customerId, instrument));
  }

  /** Último código enviado ao telefone, esperando até ser diferente de "previous". */
  private String awaitCode(String previous) {
    return await().atMost(Duration.ofSeconds(10)).until(() -> FakeWhatsApp.SENT.stream()
        .filter(s -> s.phone().equals(phone) && s.template().equals("luthieria_codigo_acesso"))
        .reduce((a, b) -> b).map(Sent::button).orElse(null),
        c -> c != null && !c.equals(previous));
  }

  private String login(String code) throws Exception {
    var body = mvc.perform(post("/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"%s\"}".formatted(code)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.refreshToken").value(org.hamcrest.Matchers.nullValue()))
        .andReturn().getResponse().getContentAsString();
    return JsonPath.read(body, "$.accessToken");
  }

  private void loginExpecting(String code, int expected) throws Exception {
    mvc.perform(post("/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"%s\"}".formatted(code)))
        .andExpect(status().is(expected));
  }
}
