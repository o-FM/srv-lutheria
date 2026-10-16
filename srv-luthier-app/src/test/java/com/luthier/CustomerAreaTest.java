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
 * Área do cliente: OS aberta → WhatsApp automático com link + código → cliente entra com
 * telefone + código e vê só as próprias OS.
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
  String slug;
  String phone;
  String orderId;

  @BeforeEach
  void openOrder() throws Exception {
    api = new ApiClient(mvc);
    admin = api.registerWorkshop("Oficina Cliente " + System.nanoTime());
    phone = "119%08d".formatted(Math.floorMod(System.nanoTime(), 100_000_000L));
    var customer = api.create(admin, "/customers",
        "{\"name\":\"Lucas Silveira\",\"phone\":\"%s\"}".formatted(phone));
    var instrument = api.create(admin, "/customers/" + customer + "/instruments",
        "{\"type\":\"guitarra\",\"brand\":\"Fender\",\"model\":\"Strat\"}");
    orderId = api.create(admin, "/service-orders", """
        {"customerId":"%s","instrumentId":"%s","reportedIssue":"Trastejando",
         "services":[{"title":"Regulagem","laborPrice":180}]}""".formatted(customer, instrument));
  }

  @Test
  void openingAnOrderSendsLinkAndCodeAndCustomerSeesOnlyOwnOrders() throws Exception {
    // WhatsApp automático: mensagem de OS aberta (com o link) + código
    var code = awaitCode();
    var opened = FakeWhatsApp.SENT.stream()
        .filter(s -> s.phone().equals(phone) && s.template().equals("luthieria_os_aberta"))
        .findFirst().orElseThrow();
    assertThat(opened.params().get(3)).contains("/cliente/");
    slug = opened.params().get(3).substring(opened.params().get(3).lastIndexOf('/') + 1);

    // Histórico da oficina não guarda o código
    mvc.perform(auth(get("/notifications?channel=whatsapp"), admin))
        .andExpect(jsonPath("$.content[?(@.subject == 'Código de acesso')].body")
            .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString(code)))));

    var client = login(code);
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
    // Código é de uso único
    loginExpecting(code, 401);
  }

  @Test
  void wrongCodesAreLimitedAndNewCodeCanBeRequested() throws Exception {
    var code = awaitCode();
    slug = workshopSlug();
    var wrong = code.equals("000000") ? "111111" : "000000";
    for (int i = 0; i < 5; i++) {
      loginExpecting(wrong, 401);
    }
    // Após 5 erros o código certo também deixa de valer
    loginExpecting(code, 401);

    FakeWhatsApp.SENT.removeIf(s -> s.phone().equals(phone));
    mvc.perform(post("/auth/customer/request-code").contentType(MediaType.APPLICATION_JSON)
            .content("{\"workshop\":\"%s\",\"phone\":\"%s\"}".formatted(slug, phone)))
        .andExpect(status().isAccepted());
    var fresh = awaitCode();
    assertThat(login(fresh)).isNotBlank();

    // Telefone desconhecido: mesma resposta, nada é enviado
    mvc.perform(post("/auth/customer/request-code").contentType(MediaType.APPLICATION_JSON)
            .content("{\"workshop\":\"%s\",\"phone\":\"11900000000\"}".formatted(slug)))
        .andExpect(status().isAccepted());
  }

  // ---- helpers ----

  private String awaitCode() {
    return await().atMost(Duration.ofSeconds(10)).until(() -> FakeWhatsApp.SENT.stream()
        .filter(s -> s.phone().equals(phone) && s.template().equals("luthieria_codigo_acesso"))
        .reduce((a, b) -> b).map(Sent::button).orElse(null), c -> c != null);
  }

  private String workshopSlug() {
    var link = await().atMost(Duration.ofSeconds(10)).until(() -> FakeWhatsApp.SENT.stream()
        .filter(s -> s.phone().equals(phone) && s.template().equals("luthieria_os_aberta"))
        .findFirst().map(s -> s.params().get(3)).orElse(null), l -> l != null);
    return link.substring(link.lastIndexOf('/') + 1);
  }

  private String login(String code) throws Exception {
    var body = mvc.perform(post("/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"workshop\":\"%s\",\"phone\":\"%s\",\"code\":\"%s\"}".formatted(slug, phone, code)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    return JsonPath.read(body, "$.accessToken");
  }

  private void loginExpecting(String code, int expected) throws Exception {
    mvc.perform(post("/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"workshop\":\"%s\",\"phone\":\"%s\",\"code\":\"%s\"}".formatted(slug, phone, code)))
        .andExpect(status().is(expected));
  }
}
