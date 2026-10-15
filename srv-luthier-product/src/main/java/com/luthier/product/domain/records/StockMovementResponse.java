package com.luthier.product.domain.records;

import com.luthier.product.domain.entities.StockMovementEntity;
import com.luthier.product.domain.enums.MovementType;
import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(UUID id, UUID stockItemId, Instant date, MovementType type,
                                    int quantity, int delta, int balanceAfter, String reason,
                                    UUID orderId, String createdBy) {

  public static StockMovementResponse from(StockMovementEntity m) {
    return new StockMovementResponse(m.getId(), m.getProductId(), m.getCreatedAt(), m.getType(),
        m.getQuantity(), m.getDelta(), m.getBalanceAfter(), m.getReason(), m.getOrderId(),
        m.getCreatedBy());
  }
}
