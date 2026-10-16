package com.luthier.service.services.impl;

import com.luthier.customer.CustomerQueries;
import com.luthier.service.domain.records.CustomerDataExport;
import com.luthier.service.domain.records.OrderResponse;
import com.luthier.service.domain.repositories.ServiceOrderRepository;
import com.luthier.service.services.PrivacyService;
import com.luthier.shared.audit.AuditEvent;
import com.luthier.shared.errors.NotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivacyServiceImpl implements PrivacyService {

  private final CustomerQueries customers;
  private final ServiceOrderRepository orders;
  private final ApplicationEventPublisher events;

  public PrivacyServiceImpl(CustomerQueries customers, ServiceOrderRepository orders,
      ApplicationEventPublisher events) {
    this.customers = customers;
    this.orders = orders;
    this.events = events;
  }

  @Override
  @Transactional(readOnly = true)
  public CustomerDataExport exportCustomer(UUID customerId) {
    var customer = customers.exportData(customerId)
        .orElseThrow(() -> new NotFoundException("Customer not found!"));
    var history = orders.findAllByCustomerIdOrderByOrderNumberDesc(customerId).stream()
        .map(OrderResponse::from)
        .toList();

    // Exportar dados pessoais é ação sensível: fica na trilha de auditoria
    events.publishEvent(AuditEvent.of("CUSTOMER_EXPORTED", "customer", customerId, null));

    return new CustomerDataExport(Instant.now(),
        "Dados pessoais e histórico de serviços mantidos por esta luthieria (LGPD, art. 18).",
        customer, history);
  }
}
