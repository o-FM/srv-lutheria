package com.luthier.product.services.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.luthier.product.StockConsumptionRequested;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * PRO-01: só a corrida com outra entrega do MESMO evento pode ser ignorada. Qualquer outra violação
 * de integridade na baixa (ex.: referência longa demais, CHECK de estoque) desfaz a transação, e o
 * listener precisa relançar para o outbox reprocessar. Engolir a exceção faria a publicação ser
 * dada como concluída sem nenhuma baixa.
 */
class StockConsumptionListenerTest {

  private final StockConsumptionListener.Consumer consumer =
      mock(StockConsumptionListener.Consumer.class);
  private final StockConsumptionListener listener = new StockConsumptionListener(consumer);
  private final StockConsumptionRequested event = new StockConsumptionRequested(
      UUID.randomUUID(), UUID.randomUUID(), "OS #7", "teste",
      List.of(new StockConsumptionRequested.Item(UUID.randomUUID(), 1)));

  @Test
  void raceWithAnotherDeliveryOfTheSameOrderIsIgnored() {
    when(consumer.consume(event)).thenThrow(new DataIntegrityViolationException("uk"));
    when(consumer.alreadyApplied(event.orderId())).thenReturn(true);

    assertThatCode(() -> listener.on(event)).doesNotThrowAnyException();
  }

  @Test
  void otherIntegrityFailuresAreRethrownSoTheOutboxRetries() {
    var failure = new DataIntegrityViolationException("value too long");
    when(consumer.consume(event)).thenThrow(failure);
    when(consumer.alreadyApplied(event.orderId())).thenReturn(false);

    assertThatThrownBy(() -> listener.on(event)).isSameAs(failure);
  }
}
