package com.luthier.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Atalhos para os testes de fluxo: cadastro de luthieria, login e chamadas autenticadas. */
public final class ApiClient {

  private final MockMvc mvc;

  public ApiClient(MockMvc mvc) {
    this.mvc = mvc;
  }

  /** Cadastra uma luthieria nova e devolve o access token do dono (ADMIN). */
  public String registerWorkshop(String name) throws Exception {
    var email = "dono-" + System.nanoTime() + "@teste.com";
    var body = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"workshop":{"name":"%s"},
                 "owner":{"name":"Dono","email":"%s","password":"SenhaForte@2026"}}"""
                .formatted(name, email)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return JsonPath.read(body, "$.accessToken");
  }

  /** Cria um usuário com o papel informado na luthieria do admin e devolve o token dele. */
  public String createUser(String adminToken, String profile) throws Exception {
    var email = profile.toLowerCase() + "-" + System.nanoTime() + "@teste.com";
    mvc.perform(auth(post("/users"), adminToken).content("""
            {"name":"Usuário","email":"%s","password":"SenhaForte@2026","profile":"%s"}"""
            .formatted(email, profile)))
        .andExpect(status().isCreated());
    var login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"password\":\"SenhaForte@2026\"}".formatted(email)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    return JsonPath.read(login, "$.accessToken");
  }

  /** POST autenticado esperando 201; devolve o campo "id" (ou o JSONPath pedido). */
  public String create(String token, String path, String json, String jsonPath) throws Exception {
    var body = mvc.perform(auth(post(path), token).content(json))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return JsonPath.read(body, jsonPath).toString();
  }

  public String create(String token, String path, String json) throws Exception {
    return create(token, path, json, "$.id");
  }

  public static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req,
      String token) {
    return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
  }
}
