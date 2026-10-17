package com.luthier;

import static com.luthier.support.ApiClient.auth;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.luthier.support.ApiClient;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Autoatendimento da conta (PATCH /users/me) e fotos da OS, inclusive pelo portal. */
@SpringBootTest(properties = {
    "app.security.rate-limit.login.capacity=1000",
    "app.security.rate-limit.portal.capacity=1000"})
@AutoConfigureMockMvc
class AccountAndPhotoTest {

  /** Menor JPEG válido o bastante para a checagem dos bytes iniciais. */
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10,
      'J', 'F', 'I', 'F', 0, 1, (byte) 0xFF, (byte) 0xD9};

  @Autowired
  MockMvc mvc;

  ApiClient api;
  String admin;

  @BeforeEach
  void setUp() throws Exception {
    api = new ApiClient(mvc);
    admin = api.registerWorkshop("Oficina Conta");
  }

  @Test
  void accountUpdateRequiresCurrentPasswordAndRotatesSession() throws Exception {
    mvc.perform(auth(patch("/users/me"), admin)
            .content("{\"currentPassword\":\"errada-123456\",\"phone\":\"(11) 91234-5678\"}"))
        .andExpect(status().isBadRequest());

    // Só telefone: sem sessão nova
    mvc.perform(auth(patch("/users/me"), admin)
            .content("{\"currentPassword\":\"SenhaForte@2026\",\"phone\":\"(11) 91234-5678\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.phone").value("11912345678"))
        .andExpect(jsonPath("$.session").doesNotExist());

    var email = "novo-" + System.nanoTime() + "@teste.com";
    var body = mvc.perform(auth(patch("/users/me"), admin).content("""
            {"currentPassword":"SenhaForte@2026","email":"%s","newPassword":"OutraSenha@2026"}"""
            .formatted(email)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value(email))
        .andExpect(jsonPath("$.session.accessToken").isNotEmpty())
        .andReturn().getResponse().getContentAsString();
    String newRefresh = JsonPath.read(body, "$.session.refreshToken");

    mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"password\":\"OutraSenha@2026\"}".formatted(email)))
        .andExpect(status().isOk());
    mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"%s\"}".formatted(newRefresh)))
        .andExpect(status().isOk());

    mvc.perform(auth(patch("/users/me"), admin)
            .content("{\"currentPassword\":\"OutraSenha@2026\",\"newPassword\":\"curta\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void photosAreStoredListedAndServedToStaffAndPortal() throws Exception {
    var customerId = api.create(admin, "/customers",
        "{\"name\":\"Lucas Silveira\",\"phone\":\"(11) 98765-4321\"}");
    var instrumentId = api.create(admin, "/customers/" + customerId + "/instruments",
        "{\"type\":\"guitarra\",\"brand\":\"Fender\",\"model\":\"Stratocaster\"}");
    var orderId = api.create(admin, "/service-orders", """
        {"customerId":"%s","instrumentId":"%s","reportedIssue":"Trastejando"}"""
        .formatted(customerId, instrumentId));

    var dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(JPEG);
    var photoId = api.create(admin, "/service-orders/" + orderId + "/photos", """
        {"tag":"entrada","caption":"Risco no tampo","dataUrl":"%s"}""".formatted(dataUrl));

    // HTML disfarçado de imagem é recusado
    var fake = "data:image/png;base64," + Base64.getEncoder().encodeToString(
        "<html><script>alert(1)</script></html>".getBytes());
    mvc.perform(auth(post("/service-orders/" + orderId + "/photos"), admin)
            .content("{\"tag\":\"entrada\",\"dataUrl\":\"%s\"}".formatted(fake)))
        .andExpect(status().isBadRequest());

    mvc.perform(auth(get("/service-orders/" + orderId + "/photos"), admin))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].tag").value("entrada"))
        .andExpect(jsonPath("$[0].caption").value("Risco no tampo"));
    mvc.perform(auth(get("/service-orders/" + orderId + "/photos/" + photoId + "/content"), admin))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/jpeg"))
        .andExpect(content().bytes(JPEG));
    mvc.perform(auth(get("/service-orders/" + orderId), admin))
        .andExpect(jsonPath("$.timeline[0].type").value("photo_added"));

    // Outra luthieria não enxerga
    var other = api.registerWorkshop("Outra");
    mvc.perform(auth(get("/service-orders/" + orderId + "/photos/" + photoId + "/content"), other))
        .andExpect(status().isNotFound());

    // Portal do cliente: lista e baixa sem login
    var order = mvc.perform(auth(get("/service-orders/" + orderId), admin))
        .andReturn().getResponse().getContentAsString();
    String token = JsonPath.read(order, "$.clientTrackingToken");
    mvc.perform(get("/portal/orders/" + token))
        .andExpect(jsonPath("$.photos[0].id").value(photoId))
        .andExpect(jsonPath("$.photos[0].user").doesNotExist());
    mvc.perform(get("/portal/orders/" + token + "/photos/" + photoId))
        .andExpect(status().isOk())
        .andExpect(content().bytes(JPEG));

    mvc.perform(auth(delete("/service-orders/" + orderId + "/photos/" + photoId), admin))
        .andExpect(status().isNoContent());
    mvc.perform(get("/portal/orders/" + token + "/photos/" + photoId))
        .andExpect(status().isNotFound());
  }
}
