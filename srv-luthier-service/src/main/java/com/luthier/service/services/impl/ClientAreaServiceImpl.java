package com.luthier.service.services.impl;

import com.luthier.customer.CustomerQueries;
import com.luthier.service.ServiceOrderStatusChanged;
import com.luthier.service.domain.entities.ServiceOrderEntity;
import com.luthier.service.domain.records.PortalOrderResponse;
import com.luthier.service.domain.repositories.OrderPhotoRepository;
import com.luthier.service.domain.repositories.ServiceOrderRepository;
import com.luthier.service.services.ClientAreaService;
import com.luthier.shared.errors.NotFoundException;
import com.luthier.shared.tenancy.CustomerContext;
import com.luthier.shared.tenancy.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClientAreaServiceImpl implements ClientAreaService {

  private static final String CUSTOMER = "Cliente (app)";

  private final ServiceOrderRepository orders;
  private final CustomerQueries customers;
  private final ApplicationEventPublisher events;
  private final OrderPhotoRepository photos;

  public ClientAreaServiceImpl(ServiceOrderRepository orders, CustomerQueries customers,
      ApplicationEventPublisher events, OrderPhotoRepository photos) {
    this.orders = orders;
    this.customers = customers;
    this.events = events;
    this.photos = photos;
  }

  @Override
  @Transactional(readOnly = true)
  public ClientProfile me() {
    var customer = customers.findCustomer(CustomerContext.require())
        .orElseThrow(() -> new NotFoundException("Customer not found!"));
    return new ClientProfile(customer.id(), customer.name(), null);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PortalOrderResponse> orders() {
    return orders.findAllByCustomerIdOrderByOrderNumberDesc(CustomerContext.require()).stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public PortalOrderResponse order(UUID orderId) {
    return toResponse(find(orderId));
  }

  @Override
  public PortalOrderResponse approveQuote(UUID orderId) {
    var order = find(orderId);
    var previous = order.getStatus();
    order.approveQuote(CUSTOMER);
    orders.saveAndFlush(order);
    publish(order, previous.getValue());
    return toResponse(order);
  }

  @Override
  public PortalOrderResponse rejectQuote(UUID orderId, String reason) {
    var order = find(orderId);
    var previous = order.getStatus();
    order.rejectQuote(reason, CUSTOMER);
    orders.saveAndFlush(order);
    publish(order, previous.getValue());
    return toResponse(order);
  }

  /** OS de outro cliente (mesma luthieria) também é 404. */
  private ServiceOrderEntity find(UUID orderId) {
    return orders.findByIdAndCustomerId(orderId, CustomerContext.require())
        .orElseThrow(() -> new NotFoundException("Order not found!"));
  }

  private PortalOrderResponse toResponse(ServiceOrderEntity order) {
    var name = customers.findCustomer(order.getCustomerId())
        .map(CustomerQueries.CustomerSummary::name).orElse(null);
    var instrument = customers.findInstrument(order.getInstrumentId())
        .map(i -> i.brand() + " " + i.model()).orElse(null);
    return PortalOrderResponse.from(order, name, instrument,
        photos.listByOrder(order.getId()));
  }

  private void publish(ServiceOrderEntity order, String previous) {
    events.publishEvent(new ServiceOrderStatusChanged(TenantContext.require(), order.getId(),
        order.getOrderNumber(), order.getCustomerId(), previous, order.getStatus().getValue(),
        order.grandTotal(), order.balance(), order.getTrackingToken()));
  }
}
