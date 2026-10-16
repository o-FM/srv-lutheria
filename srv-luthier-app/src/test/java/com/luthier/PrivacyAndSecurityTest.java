package com.luthier;

import static com.luthier.support.ApiClient.auth;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.luthier.support.ApiClient;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** LGPD (exportação, anonimização, auditoria) e endurecimento do JWT. */
@SpringBootTest(properties = "app.security.rate-limit.login.capacity=1000")
@AutoConfigureMockMvc
class PrivacyAndSecurityTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  SecretKey jwtKey;

  ApiClient api;
  String admin;

  @BeforeEach
  void setUp() throws Exception {
    api = new ApiClient(mvc);
    admin = api.registerWorkshop("Oficina LGPD");
  }

  @Test
  void exportBringsAllPersonalDataAndIsAudited() throws Exception {
    var customer = api.create(admin, "/customers", """
        {"name":"Maria Souza","phone":"11912345678","email":"maria@teste.com",
         "cpf":"529.982.247-25","address":"Rua A, 10"}""");
    var instrument = api.create(admin, "/customers/" + customer + "/instruments",
        "{\"type\":\"violao_nylon\",\"brand\":\"Di Giorgio\",\"model\":\"Tarrega\"}");
    api.create(admin, "/service-orders", """
        {"customerId":"%s","instrumentId":"%s","reportedIssue":"Cavalete solto"}"""
        .formatted(customer, instrument));

    mvc.perform(auth(get("/privacy/customers/" + customer + "/export"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customer.cpf").value("52998224725"))
        .andExpect(jsonPath("$.customer.instruments[0].brand").value("Di Giorgio"))
        .andExpect(jsonPath("$.serviceOrders[0].reportedIssue").value("Cavalete solto"));

    mvc.perform(auth(get("/audit-logs"), admin))
        .andExpect(jsonPath("$.content[*].action", hasItem("CUSTOMER_EXPORTED")))
        .andExpect(jsonPath("$.content[*].action", hasItem("TENANT_REGISTERED")));
  }

  @Test
  void anonymizationErasesPersonalDataEverywhere() throws Exception {
    var customer = api.create(admin, "/customers",
        "{\"name\":\"João Pereira\",\"phone\":\"11955554444\",\"email\":\"joao@teste.com\"}");
    var instrument = api.create(admin, "/customers/" + customer + "/instruments",
        "{\"type\":\"cavaquinho\",\"brand\":\"Rozini\",\"model\":\"RC10\"}");
    var order = api.create(admin, "/service-orders", """
        {"customerId":"%s","instrumentId":"%s","reportedIssue":"Troca de cordas",
         "services":[{"title":"Troca de cordas","laborPrice":50}]}"""
        .formatted(customer, instrument));
    mvc.perform(auth(post("/service-orders/" + order + "/status"), admin)
        .content("{\"status\":\"orcamento\"}"));
    mvc.perform(auth(post("/service-orders/" + order + "/quote/send"), admin));
    // Mensagem de orçamento (balcão) + OS aberta (API do WhatsApp, desligada no teste)
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        mvc.perform(auth(get("/notifications?channel=whatsapp"), admin))
            .andExpect(jsonPath("$.totalElements").value(2)));

    mvc.perform(auth(post("/customers/" + customer + "/anonymize"), admin))
        .andExpect(status().isNoContent());

    mvc.perform(auth(get("/customers/" + customer), admin)).andExpect(status().isNotFound());
    mvc.perform(auth(get("/privacy/customers/" + customer + "/export"), admin))
        .andExpect(jsonPath("$.customer.name").value("Cliente anonimizado"))
        .andExpect(jsonPath("$.customer.email").doesNotExist())
        // Histórico da OS continua (obrigação fiscal), sem identificar a pessoa
        .andExpect(jsonPath("$.serviceOrders.length()").value(1));
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        mvc.perform(auth(get("/notifications?channel=whatsapp"), admin))
            .andExpect(jsonPath("$.content[*].recipient", everyItem(nullValue())))
            .andExpect(jsonPath("$.content[*].actionUrl", everyItem(nullValue()))));
    mvc.perform(auth(get("/audit-logs"), admin))
        .andExpect(jsonPath("$.content[*].action", hasItem("CUSTOMER_ANONYMIZED")));
  }

  @Test
  void auditTrailRecordsLoginsAndIsPerWorkshopAndAdminOnly() throws Exception {
    var attendant = api.createUser(admin, "ATENDENTE");
    mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"inexistente@teste.com\",\"password\":\"qualquercoisa\"}"))
        .andExpect(status().isUnauthorized());

    mvc.perform(auth(get("/audit-logs"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].action", hasItem("USER_CREATED")))
        .andExpect(jsonPath("$.content[*].action", hasItem("LOGIN_SUCCESS")));
    mvc.perform(auth(get("/audit-logs"), attendant)).andExpect(status().isForbidden());

    var other = api.registerWorkshop("Outra LGPD");
    mvc.perform(auth(get("/audit-logs"), other))
        .andExpect(jsonPath("$.content[*].action", not(hasItem("USER_CREATED"))));
  }

  @Test
  void tokensFromOtherIssuersOrWithoutTenantAreRejected() throws Exception {
    mvc.perform(auth(get("/customers"), forge("outro-emissor", UUID.randomUUID().toString())))
        .andExpect(status().isUnauthorized());
    mvc.perform(auth(get("/customers"), forge("luthieria", null)))
        .andExpect(status().isUnauthorized());
    mvc.perform(auth(get("/customers"), forge("luthieria", UUID.randomUUID().toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  private String forge(String issuer, String tenantId) {
    var claims = JwtClaimsSet.builder()
        .issuer(issuer)
        .subject("forjado@teste.com")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .claim("roles", List.of("ADMIN"));
    if (tenantId != null) {
      claims.claim("tid", tenantId);
    }
    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey))
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            claims.build()))
        .getTokenValue();
  }
}
