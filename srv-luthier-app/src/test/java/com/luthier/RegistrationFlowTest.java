package com.luthier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Fluxo de cadastro ponta a ponta, com foco no isolamento entre luthierias (multi-tenant em
 * banco compartilhado): cada teste cadastra duas luthierias novas e confere que uma não enxerga
 * os dados da outra.
 */
@SpringBootTest(properties = "app.security.rate-limit.login.capacity=1000")
@AutoConfigureMockMvc
class RegistrationFlowTest {

  private static final String CPF = "529.982.247-25";

  @Autowired
  MockMvc mvc;

  String tokenA;
  String tokenB;

  @BeforeEach
  void registerTwoWorkshops() throws Exception {
    tokenA = register("Oficina A");
    tokenB = register("Oficina B");
  }

  @Test
  void registerReturnsSessionWithTenantClaim() throws Exception {
    mvc.perform(auth(get("/users/me"), tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile").value("ADMIN"))
        .andExpect(jsonPath("$.tenantId").isNotEmpty());
  }

  @Test
  void customersAreIsolatedPerWorkshop() throws Exception {
    var id = createCustomer(tokenA, "Lucas Silveira");

    // A vê, B não vê (404, não 403: não revela que existe)
    mvc.perform(auth(get("/customers/" + id), tokenA)).andExpect(status().isOk())
        .andExpect(jsonPath("$.maskedCpf").value("***.982.247-**"))
        .andExpect(jsonPath("$.phone").value("(11) 98765-4321"));
    mvc.perform(auth(get("/customers/" + id), tokenB)).andExpect(status().isNotFound());
    mvc.perform(auth(get("/customers"), tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    // CPF é único por luthieria: repetir em A conflita, em B é permitido
    mvc.perform(auth(post("/customers"), tokenA).content(customerJson("Outro")))
        .andExpect(status().isConflict());
    createCustomer(tokenB, "Lucas em B");

    // B não consegue cadastrar instrumento no cliente de A
    mvc.perform(auth(post("/customers/" + id + "/instruments"), tokenB)
            .content("{\"type\":\"baixo\",\"brand\":\"Fender\",\"model\":\"Jazz Bass\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void instrumentIsRegisteredForCustomer() throws Exception {
    var id = createCustomer(tokenA, "Carlos");

    mvc.perform(auth(post("/customers/" + id + "/instruments"), tokenA)
            .content("{\"type\":\"violao_nylon\",\"brand\":\"Giannini\",\"model\":\"GN-15\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("violao_nylon"));

    mvc.perform(auth(get("/customers/" + id), tokenA))
        .andExpect(jsonPath("$.instruments.length()").value(1));
  }

  @Test
  void invalidCustomerDataIsRejected() throws Exception {
    mvc.perform(auth(post("/customers"), tokenA)
            .content("{\"name\":\"\",\"phone\":\"123\",\"cpf\":\"111.111.111-11\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.name").exists())
        .andExpect(jsonPath("$.errors.phone").exists())
        .andExpect(jsonPath("$.errors.cpf").exists());
  }

  @Test
  void stockMovementsKeepBalanceAndAreIsolated() throws Exception {
    var body = mvc.perform(auth(post("/products"), tokenA).content("""
            {"name":"Corda 0.10","category":"cordas","sku":"exl110","unit":"jogo",
             "costPrice":20,"salePrice":40,"minQuantity":2,"initialQuantity":5}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sku").value("EXL110"))
        .andExpect(jsonPath("$.currentQuantity").value(5))
        .andReturn().getResponse().getContentAsString();
    String id = JsonPath.read(body, "$.id");

    mvc.perform(auth(post("/products/" + id + "/movements"), tokenA)
            .content("{\"type\":\"saida\",\"quantity\":4,\"reason\":\"OS 1\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balanceAfter").value(1));
    mvc.perform(auth(post("/products/" + id + "/movements"), tokenA)
            .content("{\"type\":\"saida\",\"quantity\":2,\"reason\":\"OS 2\"}"))
        .andExpect(status().isConflict());

    mvc.perform(auth(get("/products?lowStock=true"), tokenA))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(auth(get("/products/" + id), tokenB)).andExpect(status().isNotFound());
  }

  @Test
  void serviceCatalogIsPerWorkshop() throws Exception {
    mvc.perform(auth(post("/service-catalog"), tokenA)
            .content("{\"title\":\"Regulagem completa\",\"laborPrice\":180.00}"))
        .andExpect(status().isCreated());

    mvc.perform(auth(get("/service-catalog"), tokenA)).andExpect(jsonPath("$.length()").value(1));
    mvc.perform(auth(get("/service-catalog"), tokenB)).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void attendantCanRegisterCustomersButNotProducts() throws Exception {
    var email = "balcao-" + System.nanoTime() + "@teste.com";
    mvc.perform(auth(post("/users"), tokenA).content("""
            {"name":"Mariana","email":"%s","password":"Balcao@2026xx","profile":"ATENDENTE"}"""
            .formatted(email)))
        .andExpect(status().isCreated());
    var login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"password\":\"Balcao@2026xx\"}".formatted(email)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String attendant = JsonPath.read(login, "$.accessToken");

    createCustomer(attendant, "Cliente do balcão");
    mvc.perform(auth(post("/products"), attendant).content("""
            {"name":"x","category":"pecas","sku":"X1","unit":"un","costPrice":1,"salePrice":2,
             "minQuantity":0}"""))
        .andExpect(status().isForbidden());
  }

  @Test
  void eachUserKeepsOwnAppearancePreferences() throws Exception {
    var email = "cores-" + System.nanoTime() + "@teste.com";
    mvc.perform(auth(post("/users"), tokenA).content("""
            {"name":"Mariana","email":"%s","password":"Balcao@2026xx","profile":"ATENDENTE"}"""
            .formatted(email)))
        .andExpect(status().isCreated());
    var login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"password\":\"Balcao@2026xx\"}".formatted(email)))
        .andReturn().getResponse().getContentAsString();
    String attendant = JsonPath.read(login, "$.accessToken");

    mvc.perform(auth(put("/users/me/preferences"), attendant)
            .content("{\"themeMode\":\"light\",\"themeAccent\":\"violeta\",\"themeTone\":\"ardosia\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences.themeAccent").value("violeta"));
    mvc.perform(auth(put("/users/me/preferences"), tokenA)
            .content("{\"themeMode\":\"dark\",\"themeAccent\":\"azul\",\"themeTone\":\"quente\"}"))
        .andExpect(status().isOk());

    // Cada um vê a própria escolha
    mvc.perform(auth(get("/users/me"), attendant))
        .andExpect(jsonPath("$.preferences.themeMode").value("light"))
        .andExpect(jsonPath("$.preferences.themeAccent").value("violeta"));
    mvc.perform(auth(get("/users/me"), tokenA))
        .andExpect(jsonPath("$.preferences.themeAccent").value("azul"));

    // Só valores do catálogo de cores
    mvc.perform(auth(put("/users/me/preferences"), attendant)
            .content("{\"themeAccent\":\"#ff0000\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void requestsWithoutTokenAreRejected() throws Exception {
    mvc.perform(get("/customers")).andExpect(status().isUnauthorized());
  }

  // ---- helpers ----

  private String register(String workshop) throws Exception {
    var email = "dono-" + System.nanoTime() + "@teste.com";
    var body = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"workshop":{"name":"%s"},
                 "owner":{"name":"Dono","email":"%s","password":"SenhaForte@2026"}}"""
                .formatted(workshop, email)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String token = JsonPath.read(body, "$.accessToken");
    assertThat(token).isNotBlank();
    return token;
  }

  private String createCustomer(String token, String name) throws Exception {
    var body = mvc.perform(auth(post("/customers"), token).content(customerJson(name)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private static String customerJson(String name) {
    return """
        {"name":"%s","phone":"(11) 98765-4321","cpf":"%s"}""".formatted(name, CPF);
  }

  private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req,
      String token) {
    return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
  }
}
