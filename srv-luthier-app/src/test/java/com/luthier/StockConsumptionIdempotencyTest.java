package com.luthier;

import static com.luthier.RetentionJobFlowTest.tenantOf;
import static com.luthier.support.ApiClient.auth;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.luthier.product.StockConsumptionRequested;
import com.luthier.product.services.impl.StockConsumptionListener;
import com.luthier.support.ApiClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PRO-01 (docs/auditoria.md): o outbox do Spring Modulith entrega o evento AO MENOS uma vez. Se o
 * processo cai depois de baixar o estoque e antes de marcar a publicação como concluída, o mesmo
 * StockConsumptionRequested chega de novo, e a baixa não pode se repetir.
 *
 * O teste chama o listener diretamente duas vezes (pelo proxy @Async), que é exatamente o que a
 * republicação do outbox faz.
 */
@SpringBootTest(properties = "app.security.rate-limit.login.capacity=1000")
@AutoConfigureMockMvc
class StockConsumptionIdempotencyTest {

  private static final int INITIAL = 5;
  private static final int CONSUMED = 2;

  @Autowired
  MockMvc mvc;

  @Autowired
  StockConsumptionListener listener;

  @Test
  void redeliveredConsumptionIsAppliedOnlyOnce() throws Exception {
    var api = new ApiClient(mvc);
    var admin = api.registerWorkshop("Oficina Estoque Idempotente");
    var productId = api.create(admin, "/products", """
        {"name":"Tarraxa","category":"pecas","sku":"TRX-1","unit":"jogo",
         "costPrice":10,"salePrice":20,"minQuantity":0,"initialQuantity":%d}"""
        .formatted(INITIAL));
    var event = new StockConsumptionRequested(tenantOf(admin), UUID.randomUUID(), "OS #99",
        "teste", List.of(new StockConsumptionRequested.Item(UUID.fromString(productId),
            CONSUMED)));

    listener.on(event);
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        mvc.perform(auth(get("/products/" + productId), admin))
            .andExpect(jsonPath("$.currentQuantity").value(INITIAL - CONSUMED)));

    // Reentrega do outbox DEPOIS da primeira concluir. Duas chamadas simultâneas não serviriam:
    // a segunda esbarraria no @Version do produto e falharia, e só a republicação seguinte
    // repetiria a baixa.
    listener.on(event);

    // a reentrega roda em outra thread: dá tempo a ela e confere que nada mudou
    await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
        mvc.perform(auth(get("/products/" + productId), admin))
            .andExpect(jsonPath("$.currentQuantity").value(INITIAL - CONSUMED)));
    mvc.perform(auth(get("/products/" + productId + "/movements"), admin))
        .andExpect(jsonPath("$.content[?(@.orderId == '%s')]".formatted(event.orderId()),
            hasSize(1)));
  }
}
