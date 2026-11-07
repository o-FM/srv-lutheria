package com.luthier.service.domain.records;

import java.math.BigDecimal;
import java.util.UUID;

/** Totais de itens de uma OS, calculados no banco (listagem sem carregar as coleções). */
public record OrderTotals(UUID orderId, BigDecimal laborTotal, BigDecimal partsTotal) {

  public static OrderTotals empty(UUID orderId) {
    return new OrderTotals(orderId, BigDecimal.ZERO, BigDecimal.ZERO);
  }

  /** Mesma fórmula de ServiceOrderEntity.grandTotal(). */
  public BigDecimal grandTotal(BigDecimal discount) {
    return laborTotal.add(partsTotal).subtract(discount);
  }

  /** Mesma fórmula de ServiceOrderEntity.balance(). */
  public BigDecimal balance(BigDecimal discount, BigDecimal paidAmount) {
    return grandTotal(discount).subtract(paidAmount);
  }
}
