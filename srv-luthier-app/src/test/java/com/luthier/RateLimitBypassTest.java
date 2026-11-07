package com.luthier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regressão do SEC-01 (docs/auditoria.md): o rate limit comparava a URI crua, então variações
 * codificadas da mesma rota (/auth/%6cogin) chegavam ao controller sem passar pelo limitador,
 * e cada grafia ganhava um balde próprio.
 *
 * Cada teste usa uma rota diferente (os baldes são por rota + IP e o IP do MockMvc é fixo), então
 * a ordem de execução não importa. Os URIs são montados com URI.create para que o MockMvc não
 * recodifique o '%'.
 */
@SpringBootTest(properties = {
    "app.security.rate-limit.login.capacity=3",
    "app.security.rate-limit.portal.capacity=3"})
@AutoConfigureMockMvc
class RateLimitBypassTest {

  private static final int CAPACITY = 3;
  private static final int TOO_MANY_REQUESTS = 429;
  private static final String BAD_LOGIN =
      "{\"email\":\"ninguem@teste.com\",\"password\":\"senha-errada-123\"}";
  private static final String FORGOT = "{\"email\":\"ninguem@teste.com\"}";

  @Autowired
  MockMvc mvc;

  @Test
  void encodedLoginPathReachesTheSameEndpointAndIsRateLimited() throws Exception {
    // A variante codificada chega de fato ao login (401 de credencial inválida, não 404)
    assertThat(postJson("/auth/%6cogin", BAD_LOGIN)).isEqualTo(401);

    for (int i = 1; i < CAPACITY; i++) {
      postJson("/auth/%6cogin", BAD_LOGIN);
    }

    assertThat(postJson("/auth/%6cogin", BAD_LOGIN)).isEqualTo(TOO_MANY_REQUESTS);
  }

  @Test
  void differentSpellingsOfTheSameRouteShareOneBucket() throws Exception {
    assertThat(postJson("/auth/forgot-password", FORGOT)).isEqualTo(202);
    assertThat(postJson("/auth/%66orgot-password", FORGOT)).isEqualTo(202);
    assertThat(postJson("/auth/forgot-%70assword", FORGOT)).isEqualTo(202);

    assertThat(postJson("/auth/forgot-password", FORGOT)).isEqualTo(TOO_MANY_REQUESTS);
    assertThat(postJson("/auth/%66orgot-password", FORGOT)).isEqualTo(TOO_MANY_REQUESTS);
  }

  @Test
  void encodedPortalPathIsRateLimited() throws Exception {
    var plain = getStatus("/portal/orders/token-invalido");
    var encoded = getStatus("/%70ortal/orders/token-invalido");
    // Mesma rota: o controller responde igual às duas grafias
    assertThat(encoded).isEqualTo(plain).isNotEqualTo(TOO_MANY_REQUESTS);

    getStatus("/%70ortal/orders/token-invalido");

    assertThat(getStatus("/%70ortal/orders/token-invalido")).isEqualTo(TOO_MANY_REQUESTS);
  }

  @Test
  void requestsOutsideTheLimitedRoutesDoNotConsumeTheBucket() throws Exception {
    for (int i = 0; i <= CAPACITY; i++) {
      // Método diferente de POST não é limitado (e o MVC responde 405)
      assertThat(getStatus("/auth/register")).isNotEqualTo(TOO_MANY_REQUESTS);
      // Barra final não roteia no MVC (404) e não é limitada
      assertThat(postJson("/auth/register/", "{}")).isEqualTo(404);
      // Caixa diferente não é rota pública: cai no "authenticated" (401), sem consumir o balde
      assertThat(postJson("/AUTH/register", "{}")).isEqualTo(401);
    }

    // O balde de /auth/register continua cheio: chega à validação do controller (400)
    assertThat(postJson("/auth/register", "{}")).isEqualTo(400);
  }

  @Test
  void pathsRejectedByTheFirewallNeverReachTheController() throws Exception {
    assertThat(postJson("/auth//reset-password", "{}")).isEqualTo(400);
    assertThat(postJson("/auth/%2Freset-password", "{}")).isEqualTo(400);
    assertThat(postJson("/auth/reset-password;x=1", "{}")).isEqualTo(400);
  }

  private int postJson(String path, String body) throws Exception {
    return mvc.perform(post(URI.create(path))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andReturn().getResponse().getStatus();
  }

  private int getStatus(String path) throws Exception {
    return mvc.perform(get(URI.create(path))).andReturn().getResponse().getStatus();
  }
}
