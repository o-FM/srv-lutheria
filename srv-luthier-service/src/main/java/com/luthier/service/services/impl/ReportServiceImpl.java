package com.luthier.service.services.impl;

import com.luthier.customer.CustomerQueries;
import com.luthier.product.ProductQueries;
import com.luthier.service.domain.entities.ServiceOrderEntity;
import com.luthier.service.domain.enums.OrderStatus;
import com.luthier.service.domain.enums.PaymentMethod;
import com.luthier.service.domain.records.ReportResponses.Dashboard;
import com.luthier.service.domain.records.ReportResponses.Financial;
import com.luthier.service.domain.records.ReportResponses.MethodTotal;
import com.luthier.service.domain.repositories.PaymentRepository;
import com.luthier.service.domain.repositories.ServiceOrderRepository;
import com.luthier.service.services.ReportService;
import com.luthier.shared.errors.BadRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

  /** Operação da oficina no fuso de São Paulo ("hoje", períodos do financeiro). */
  static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

  private static final EnumSet<OrderStatus> PENDING_DELIVERY = EnumSet.of(OrderStatus.RECEBIDO,
      OrderStatus.DIAGNOSTICO, OrderStatus.ORCAMENTO, OrderStatus.AGUARDANDO_APROVACAO,
      OrderStatus.EM_EXECUCAO);

  private final ServiceOrderRepository orders;
  private final PaymentRepository payments;
  private final CustomerQueries customers;
  private final ProductQueries products;
  private final Clock clock;

  public ReportServiceImpl(ServiceOrderRepository orders, PaymentRepository payments,
      CustomerQueries customers, ProductQueries products) {
    this.orders = orders;
    this.payments = payments;
    this.customers = customers;
    this.products = products;
    this.clock = Clock.system(ZONE);
  }

  @Override
  public Dashboard dashboard() {
    var today = LocalDate.now(clock);

    Map<String, Long> byStatus = new LinkedHashMap<>();
    Arrays.stream(OrderStatus.values()).forEach(s -> byStatus.put(s.getValue(), 0L));
    for (var row : orders.countByStatus()) {
      byStatus.put(((OrderStatus) row[0]).getValue(), (Long) row[1]);
    }

    long open = Arrays.stream(OrderStatus.values()).filter(OrderStatus::isOpen)
        .mapToLong(s -> byStatus.get(s.getValue())).sum();

    return new Dashboard(today, byStatus, open,
        orders.countDueOn(PENDING_DELIVERY, today),
        orders.countOverdue(PENDING_DELIVERY, today),
        byStatus.get(OrderStatus.PRONTO.getValue()),
        byStatus.get(OrderStatus.AGUARDANDO_APROVACAO.getValue()),
        products.countLowStock(),
        customers.countActive());
  }

  @Override
  public Financial financial(LocalDate from, LocalDate to) {
    var end = to == null ? LocalDate.now(clock) : to;
    var start = from == null ? end.withDayOfMonth(1) : from;
    if (start.isAfter(end)) {
      throw new BadRequestException("Data inicial maior que a final");
    }
    var fromInstant = start.atStartOfDay(ZONE).toInstant();
    var toInstant = end.plusDays(1).atStartOfDay(ZONE).toInstant();

    Map<String, MethodTotal> byMethod = new TreeMap<>();
    var received = BigDecimal.ZERO;
    long paymentsCount = 0;
    for (var row : payments.sumByMethod(fromInstant, toInstant)) {
      var amount = (BigDecimal) row[1];
      var count = (Long) row[2];
      byMethod.put(((PaymentMethod) row[0]).getValue(), new MethodTotal(amount, count));
      received = received.add(amount);
      paymentsCount += count;
    }

    var delivered = orders.countDelivered(fromInstant, toInstant);
    var averageTicket = paymentsCount == 0 ? BigDecimal.ZERO
        : received.divide(BigDecimal.valueOf(paymentsCount), 2, RoundingMode.HALF_UP);
    var receivable = orders.findBillable().stream()
        .map(ServiceOrderEntity::balance)
        .filter(b -> b.signum() > 0)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new Financial(start, end, received, byMethod, delivered, averageTicket, receivable);
  }
}
