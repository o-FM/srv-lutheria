package com.luthier.product.services.impl;

import com.luthier.product.StockConsumptionRequested;
import com.luthier.product.domain.entities.StockConsumptionEntity;
import com.luthier.product.domain.enums.MovementType;
import com.luthier.product.domain.repositories.ProductRepository;
import com.luthier.product.domain.repositories.StockConsumptionRepository;
import com.luthier.shared.errors.NotFoundException;
import com.luthier.shared.tenancy.TenantContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
 *
 * Idempotente (PRO-01): o outbox entrega ao menos uma vez, então cada OS grava um marcador único
 * (StockConsumptionEntity) na mesma transação da baixa. Uma reentrega encontra o marcador e não
 * baixa de novo; terminar sem exceção faz o Modulith dar a publicação como concluída.
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
    try {
      boolean applied = TenantContext.callAs(event.tenantId(), () -> consumer.consume(event));
      if (applied) {
        log.info("Baixa de estoque concluída para {} ({} itens)", event.reference(),
            event.items().size());
      } else {
        log.info("Baixa de estoque de {} já aplicada; reentrega ignorada", event.reference());
      }
    } catch (DataIntegrityViolationException e) {
      // A transação já foi desfeita inteira. Só é corrida com outra entrega se o marcador existe;
      // a conferência abre uma transação nova (fora do consume, que está marcado para rollback).
      if (TenantContext.callAs(event.tenantId(), () -> consumer.alreadyApplied(event.orderId()))) {
        log.info("Baixa de estoque de {} já aplicada por outra entrega", event.reference());
        return;
      }
      // Outra violação (ex.: referência longa demais): relança para o outbox reprocessar, senão a
      // publicação seria dada como concluída sem nenhuma baixa
      log.error("Baixa de estoque de {} falhou por violação de integridade; o outbox vai "
          + "reprocessar", event.reference());
      throw e;
    }
  }

  @Component
  static class Consumer {

    private final ProductRepository productRepository;
    private final StockConsumptionRepository consumptionRepository;
    private final StockLedger stockLedger;

    Consumer(ProductRepository productRepository,
        StockConsumptionRepository consumptionRepository, StockLedger stockLedger) {
      this.productRepository = productRepository;
      this.consumptionRepository = consumptionRepository;
      this.stockLedger = stockLedger;
    }

    @Transactional(readOnly = true)
    public boolean alreadyApplied(UUID orderId) {
      return consumptionRepository.existsByOrderId(orderId);
    }

    /** @return false quando a baixa desta OS já tinha sido aplicada (reentrega do outbox). */
    @Transactional
    public boolean consume(StockConsumptionRequested event) {
      if (consumptionRepository.existsByOrderId(event.orderId())) {
        return false;
      }
      // Marcador ANTES dos movimentos: uma entrega concorrente esbarra na unicidade logo aqui
      consumptionRepository.saveAndFlush(
          StockConsumptionEntity.of(event.orderId(), event.reference()));
      for (var item : event.items()) {
        var product = productRepository.findById(item.productId())
            .orElseThrow(() -> new NotFoundException("Product not found: " + item.productId()));
        stockLedger.apply(product, MovementType.SAIDA, item.quantity(), event.reference(),
            event.orderId(), event.requestedBy());
      }
      return true;
    }
  }
}
