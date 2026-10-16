package com.luthier.product.services.impl;

import com.luthier.product.StockLow;
import com.luthier.product.domain.entities.ProductEntity;
import com.luthier.product.domain.entities.StockMovementEntity;
import com.luthier.product.domain.enums.MovementType;
import com.luthier.product.domain.repositories.StockMovementRepository;
import com.luthier.shared.errors.BadRequestException;
import com.luthier.shared.errors.ConflictException;
import com.luthier.shared.tenancy.TenantContext;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Única porta de alteração de saldo: toda mudança vira um movimento imutável. Usado pela API REST
 * (com usuário logado) e pela baixa automática das ordens de serviço (via evento, sem usuário).
 */
@Component
public class StockLedger {

  private final StockMovementRepository movementRepository;
  private final ApplicationEventPublisher events;

  public StockLedger(StockMovementRepository movementRepository, ApplicationEventPublisher events) {
    this.movementRepository = movementRepository;
    this.events = events;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public StockMovementEntity apply(ProductEntity product, MovementType type, int quantity,
      String reason, UUID orderId, String user) {
    int delta = switch (type) {
      case ENTRADA -> requirePositive(quantity);
      case SAIDA -> -requirePositive(quantity);
      case AJUSTE -> quantity - product.getCurrentQuantity();
    };

    if (product.getCurrentQuantity() + delta < 0) {
      throw new ConflictException("Saldo insuficiente para " + product.getName()
          + ": disponível " + product.getCurrentQuantity());
    }
    product.applyDelta(delta);

    var movement = movementRepository.save(StockMovementEntity.record(product.getId(), type,
        quantity, delta, product.getCurrentQuantity(), reason, orderId, user));

    if (delta < 0 && product.isLowStock()) {
      events.publishEvent(new StockLow(TenantContext.require(), product.getId(), product.getName(),
          product.getCurrentQuantity(), product.getMinQuantity()));
    }
    return movement;
  }

  private static int requirePositive(int quantity) {
    if (quantity <= 0) {
      throw new BadRequestException("Quantidade deve ser maior que zero");
    }
    return quantity;
  }
}
