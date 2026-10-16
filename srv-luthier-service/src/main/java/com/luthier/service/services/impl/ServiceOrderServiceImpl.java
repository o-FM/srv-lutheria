package com.luthier.service.services.impl;

import com.luthier.customer.CustomerQueries;
import com.luthier.product.ProductQueries;
import com.luthier.product.StockConsumptionRequested;
import com.luthier.service.ServiceOrderOpened;
import com.luthier.service.ServiceOrderStatusChanged;
import com.luthier.service.domain.entities.OrderCounterEntity;
import com.luthier.service.domain.entities.OrderPartEntity;
import com.luthier.service.domain.entities.ServiceOrderEntity;
import com.luthier.service.domain.enums.OrderStatus;
import com.luthier.service.domain.records.OrderRequests.AddNote;
import com.luthier.service.domain.records.OrderRequests.AddPart;
import com.luthier.service.domain.records.OrderRequests.AddService;
import com.luthier.service.domain.records.OrderRequests.ChangeStatus;
import com.luthier.service.domain.records.OrderRequests.CreateOrder;
import com.luthier.service.domain.records.OrderRequests.ReceivePayment;
import com.luthier.service.domain.records.OrderRequests.RejectQuote;
import com.luthier.service.domain.records.OrderRequests.UpdateOrder;
import com.luthier.service.domain.records.OrderResponse;
import com.luthier.service.domain.repositories.OrderCounterRepository;
import com.luthier.service.domain.repositories.ServiceCatalogRepository;
import com.luthier.service.domain.repositories.ServiceOrderRepository;
import com.luthier.service.services.ServiceOrderService;
import com.luthier.shared.authorization.Roles;
import com.luthier.shared.errors.BadRequestException;
import com.luthier.shared.errors.ConflictException;
import com.luthier.shared.errors.NotFoundException;
import com.luthier.shared.tenancy.TenantContext;
import com.luthier.shared.web.PageResponse;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServiceOrderServiceImpl implements ServiceOrderService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final ServiceOrderRepository orderRepository;
  private final OrderCounterRepository counterRepository;
  private final ServiceCatalogRepository catalogRepository;
  private final CustomerQueries customers;
  private final ProductQueries products;
  private final ApplicationEventPublisher events;

  public ServiceOrderServiceImpl(ServiceOrderRepository orderRepository,
      OrderCounterRepository counterRepository, ServiceCatalogRepository catalogRepository,
      CustomerQueries customers, ProductQueries products, ApplicationEventPublisher events) {
    this.orderRepository = orderRepository;
    this.counterRepository = counterRepository;
    this.catalogRepository = catalogRepository;
    this.customers = customers;
    this.products = products;
    this.events = events;
  }

  @Override
  public OrderResponse create(CreateOrder request, String user) {
    var instrument = customers.findInstrument(request.instrumentId())
        .filter(i -> i.customerId().equals(request.customerId()))
        .orElseThrow(() -> new NotFoundException("Instrument not found for this customer!"));
    if (!customers.exists(instrument.customerId())) {
      throw new NotFoundException("Customer not found!");
    }

    var order = ServiceOrderEntity.open(nextNumber(), request.customerId(), request.instrumentId(),
        request.priority(), request.reportedIssue().strip(), request.estimatedDeliveryDate(),
        newTrackingToken(), user);
    if (request.services() != null) {
      request.services().forEach(s -> addServiceTo(order, s));
    }
    var saved = saveAndFlush(order);
    events.publishEvent(new ServiceOrderOpened(TenantContext.require(), saved.getId(),
        saved.getOrderNumber(), saved.getCustomerId(), saved.getInstrumentId(),
        saved.getTrackingToken()));
    return OrderResponse.from(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<OrderResponse> search(OrderStatus status, UUID customerId, Integer number,
      boolean full, Pageable pageable) {
    return PageResponse.of(orderRepository.search(status, customerId, number, pageable),
        full ? OrderResponse::from : OrderResponse::summary);
  }

  @Override
  @Transactional(readOnly = true)
  public OrderResponse get(UUID id) {
    return OrderResponse.from(find(id));
  }

  @Override
  public OrderResponse update(UUID id, UpdateOrder r) {
    return mutate(id, o -> o.updateDetails(r.priority(), r.diagnosisNotes(), r.technicianNotes(),
        r.estimatedDeliveryDate(), r.discount(), r.paymentTerms()));
  }

  @Override
  public OrderResponse addService(UUID id, AddService request) {
    return mutate(id, o -> addServiceTo(o, request));
  }

  @Override
  public OrderResponse removeService(UUID id, UUID itemId) {
    return mutate(id, o -> o.removeService(itemId));
  }

  @Override
  public OrderResponse markServiceDone(UUID id, UUID itemId, String user) {
    return mutate(id, o -> o.markServiceDone(itemId, user));
  }

  @Override
  public OrderResponse addPart(UUID id, AddPart request) {
    var product = products.find(request.productId())
        .orElseThrow(() -> new NotFoundException("Product not found!"));
    return mutate(id, o -> o.addPart(product.id(), product.name(), request.quantity(),
        product.salePrice()));
  }

  @Override
  public OrderResponse removePart(UUID id, UUID partId) {
    return mutate(id, o -> o.removePart(partId));
  }

  @Override
  public OrderResponse changeStatus(UUID id, ChangeStatus request, String user) {
    var order = find(id);
    var target = request.status();

    if (target == OrderStatus.PRONTO) {
      requireBench();
      requireStockFor(order);
    }

    var previous = order.changeStatus(target, request.note(), user);
    saveAndFlush(order);

    if (target == OrderStatus.PRONTO && !order.getParts().isEmpty()) {
      // Baixa garantida e assíncrona (outbox) no módulo de produtos
      events.publishEvent(new StockConsumptionRequested(TenantContext.require(), order.getId(),
          "OS #" + order.getOrderNumber(), user,
          order.getParts().stream()
              .collect(Collectors.groupingBy(OrderPartEntity::getProductId,
                  Collectors.summingInt(OrderPartEntity::getQuantity)))
              .entrySet().stream()
              .map(e -> new StockConsumptionRequested.Item(e.getKey(), e.getValue()))
              .toList()));
    }
    publishStatus(order, previous);
    return OrderResponse.from(order);
  }

  @Override
  public OrderResponse sendQuote(UUID id, String user) {
    var order = find(id);
    var previous = order.getStatus();
    order.sendQuote(user);
    saveAndFlush(order);
    publishStatus(order, previous);
    return OrderResponse.from(order);
  }

  @Override
  public OrderResponse approveQuote(UUID id, String user) {
    var order = find(id);
    var previous = order.getStatus();
    order.approveQuote(user);
    saveAndFlush(order);
    publishStatus(order, previous);
    return OrderResponse.from(order);
  }

  @Override
  public OrderResponse rejectQuote(UUID id, RejectQuote request, String user) {
    var order = find(id);
    var previous = order.getStatus();
    order.rejectQuote(request.reason(), user);
    saveAndFlush(order);
    publishStatus(order, previous);
    return OrderResponse.from(order);
  }

  @Override
  public OrderResponse addNote(UUID id, AddNote request, String user) {
    return mutate(id, o -> o.addNote(request.note().strip(), user));
  }

  @Override
  public OrderResponse receivePayment(UUID id, ReceivePayment request, String user) {
    return mutate(id, o -> o.receivePayment(request.amount(), request.method(), user));
  }

  // ---------------- helpers ----------------

  private void addServiceTo(ServiceOrderEntity order, AddService s) {
    if (s.catalogId() != null) {
      var catalog = catalogRepository.findByIdAndActiveTrue(s.catalogId())
          .orElseThrow(() -> new NotFoundException("Service not found in catalog!"));
      order.addService(catalog.getId(), catalog.getTitle(),
          s.description() != null ? s.description() : catalog.getDescription(),
          s.laborPrice() != null ? s.laborPrice() : catalog.getLaborPrice());
      return;
    }
    if (s.title() == null || s.title().isBlank() || s.laborPrice() == null) {
      throw new BadRequestException("Informe catalogId ou título e preço do serviço");
    }
    order.addService(null, s.title().strip(), s.description(), s.laborPrice());
  }

  /** Confere o saldo ANTES de concluir: a baixa assíncrona não deve falhar por falta de peça. */
  private void requireStockFor(ServiceOrderEntity order) {
    order.getParts().stream()
        .collect(Collectors.groupingBy(OrderPartEntity::getProductId,
            Collectors.summingInt(OrderPartEntity::getQuantity)))
        .forEach((productId, quantity) -> {
          var product = products.find(productId)
              .orElseThrow(() -> new ConflictException("Peça não está mais no estoque"));
          if (product.currentQuantity() < quantity) {
            throw new ConflictException("Saldo insuficiente de " + product.name()
                + ": necessário " + quantity + ", disponível " + product.currentQuantity());
          }
        });
  }

  private static void requireBench() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    var allowed = auth != null && auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(a -> a.equals("ROLE_" + Roles.LUTHIER) || a.equals("ROLE_" + Roles.ADMIN));
    if (!allowed) {
      throw new AccessDeniedException("Somente o luthier conclui a OS");
    }
  }

  private void publishStatus(ServiceOrderEntity order, OrderStatus previous) {
    events.publishEvent(new ServiceOrderStatusChanged(TenantContext.require(), order.getId(),
        order.getOrderNumber(), order.getCustomerId(), previous.getValue(),
        order.getStatus().getValue(), order.grandTotal(), order.balance(),
        order.getTrackingToken()));
  }

  private OrderResponse mutate(UUID id, Consumer<ServiceOrderEntity> change) {
    var order = find(id);
    change.accept(order);
    return OrderResponse.from(saveAndFlush(order));
  }

  private ServiceOrderEntity saveAndFlush(ServiceOrderEntity order) {
    return orderRepository.saveAndFlush(order);
  }

  private ServiceOrderEntity find(UUID id) {
    return orderRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Service order not found!"));
  }

  private int nextNumber() {
    var counter = counterRepository.lockCurrent()
        .orElseGet(() -> counterRepository.saveAndFlush(OrderCounterEntity.start()));
    return counter.next();
  }

  private static String newTrackingToken() {
    var bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
