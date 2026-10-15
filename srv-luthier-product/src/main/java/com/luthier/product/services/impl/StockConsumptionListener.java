package com.luthier.product.services.impl;

import com.luthier.product.StockConsumptionRequested;
import com.luthier.product.domain.enums.MovementType;
import com.luthier.product.domain.repositories.ProductRepository;
import com.luthier.shared.errors.NotFoundException;
import com.luthier.shared.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Baixa de estoque assíncrona, após o commit de quem pediu. Se falhar (ex.: saldo insuficiente),
 * a publicação fica incompleta no outbox do Modulith para reprocessamento — nada se perde.
 *
 * O tenant vem no evento (thread assíncrona não tem requisição/JWT) e precisa estar no contexto
 * ANTES de abrir a transação, porque a sessão do Hibernate fixa o tenant na abertura.
 */
@Component
public class StockConsumptionListener {

  private static final Logger log = LoggerFactory.getLogger(StockConsumptionListener.class);

  private final Consumer consumer;

  public StockConsumptionListener(Consumer consumer) {
    this.consumer = consumer;
  }

  @Async
  @TransactionalEventListener
  public void on(StockConsumptionRequested event) {
    TenantContext.runAs(event.tenantId(), () -> consumer.consume(event));
    log.info("Baixa de estoque concluída para {} ({} itens)", event.reference(),
        event.items().size());
  }

  @Component
  static class Consumer {

    private final ProductRepository productRepository;
    private final StockLedger stockLedger;

    Consumer(ProductRepository productRepository, StockLedger stockLedger) {
      this.productRepository = productRepository;
      this.stockLedger = stockLedger;
    }

    @Transactional
    public void consume(StockConsumptionRequested event) {
      for (var item : event.items()) {
        var product = productRepository.findById(item.productId())
            .orElseThrow(() -> new NotFoundException("Product not found: " + item.productId()));
        stockLedger.apply(product, MovementType.SAIDA, item.quantity(), event.reference(),
            event.orderId(), event.requestedBy());
      }
    }
  }
}
