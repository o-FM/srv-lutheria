package com.luthier.service.services.impl;

import com.luthier.service.ServiceOrderStatusChanged;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Métricas de negócio (Prometheus): transições de status das OS. Ex.: taxa de orçamentos
 * aprovados = rate(luthieria_orders_transitions_total{to="em_execucao"}) /
 * rate(luthieria_orders_transitions_total{to="aguardando_aprovacao"}).
 * Sem tenantId como tag de propósito (cardinalidade ilimitada).
 */
@Component
class OrderMetrics {

  private final MeterRegistry registry;

  OrderMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  @EventListener
  void on(ServiceOrderStatusChanged event) {
    registry.counter("luthieria.orders.transitions", "from", event.from(), "to", event.to())
        .increment();
  }
}
