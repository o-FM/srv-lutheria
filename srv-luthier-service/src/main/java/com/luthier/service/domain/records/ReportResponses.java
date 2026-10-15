package com.luthier.service.domain.records;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public final class ReportResponses {

  private ReportResponses() {}

  /** Tela "Hoje": métricas operacionais do dia. */
  public record Dashboard(LocalDate date, Map<String, Long> ordersByStatus, long openOrders,
                          long dueToday, long overdue, long readyForPickup,
                          long awaitingApproval, long lowStockItems, long activeCustomers) {}

  /** Financeiro: recebimentos no período e valores a receber. */
  public record Financial(LocalDate from, LocalDate to, BigDecimal received,
                          Map<String, MethodTotal> byMethod, long deliveredOrders,
                          BigDecimal averageTicket, BigDecimal receivable) {}

  public record MethodTotal(BigDecimal amount, long count) {}
}
