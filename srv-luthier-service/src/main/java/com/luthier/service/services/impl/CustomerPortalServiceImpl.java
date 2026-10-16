package com.luthier.service.services.impl;

import com.luthier.customer.CustomerQueries;
import com.luthier.service.ServiceOrderStatusChanged;
import com.luthier.service.domain.entities.ServiceOrderEntity;
import com.luthier.service.domain.records.PhotoContent;
import com.luthier.service.domain.records.PortalOrderResponse;
import com.luthier.service.domain.repositories.OrderPhotoRepository;
import com.luthier.service.domain.repositories.ServiceOrderRepository;
import com.luthier.service.services.CustomerPortalService;
import com.luthier.shared.errors.NotFoundException;
import com.luthier.shared.tenancy.TenantContext;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A rota é pública (sem JWT, logo sem tenant). Fluxo em dois passos:
 *  1. descobre a luthieria dona do token numa consulta "de plataforma" (sem filtro de tenant);
 *  2. executa o caso de uso DENTRO do tenant encontrado — nova transação/sessão já filtrada,
 *     então tudo que for gravado (timeline, eventos) fica na luthieria certa.
 */
@Service
public class CustomerPortalServiceImpl implements CustomerPortalService {

  private static final String CUSTOMER = "Cliente (portal)";

  private final Lookup lookup;
  private final Actions actions;

  public CustomerPortalServiceImpl(Lookup lookup, Actions actions) {
    this.lookup = lookup;
    this.actions = actions;
  }

  @Override
  public PortalOrderResponse view(String token) {
    return inTenantOf(token, () -> actions.view(token));
  }

  @Override
  public PortalOrderResponse approveQuote(String token) {
    return inTenantOf(token, () -> actions.approve(token));
  }

  @Override
  public PortalOrderResponse rejectQuote(String token, String reason) {
    return inTenantOf(token, () -> actions.reject(token, reason));
  }

  @Override
  public PhotoContent photo(String token, UUID photoId) {
    return inTenantOf(token, () -> actions.photo(token, photoId));
  }

  private <T> T inTenantOf(String token, Supplier<T> action) {
    // Mesmo que um usuário logado abra o portal, o tenant é sempre o da OS dona do token
    var tenantId = TenantContext.callAsPlatform(() -> lookup.tenantOf(token));
    return TenantContext.callAs(tenantId, action);
  }

  @Service
  static class Lookup {

    private final ServiceOrderRepository repository;

    Lookup(ServiceOrderRepository repository) {
      this.repository = repository;
    }

    @Transactional(readOnly = true)
    public UUID tenantOf(String token) {
      return repository.findTenantByTrackingToken(token)
          .orElseThrow(() -> new NotFoundException("Order not found!"));
    }
  }

  @Service
  @Transactional
  static class Actions {

    private final ServiceOrderRepository repository;
    private final CustomerQueries customers;
    private final ApplicationEventPublisher events;
    private final OrderPhotoRepository photos;

    Actions(ServiceOrderRepository repository, CustomerQueries customers,
        ApplicationEventPublisher events, OrderPhotoRepository photos) {
      this.photos = photos;
      this.repository = repository;
      this.customers = customers;
      this.events = events;
    }

    @Transactional(readOnly = true)
    public PortalOrderResponse view(String token) {
      return toResponse(find(token));
    }

    public PortalOrderResponse approve(String token) {
      var order = find(token);
      var previous = order.getStatus();
      order.approveQuote(CUSTOMER);
      repository.saveAndFlush(order);
      publish(order, previous.getValue());
      return toResponse(order);
    }

    public PortalOrderResponse reject(String token, String reason) {
      var order = find(token);
      var previous = order.getStatus();
      order.rejectQuote(reason, CUSTOMER);
      repository.saveAndFlush(order);
      publish(order, previous.getValue());
      return toResponse(order);
    }

    @Transactional(readOnly = true)
    public PhotoContent photo(String token, UUID photoId) {
      var order = find(token);
      return photos.findByIdAndOrderId(photoId, order.getId())
          .map(p -> new PhotoContent(p.getContentType(), p.getData()))
          .orElseThrow(() -> new NotFoundException("Photo not found!"));
    }

    private ServiceOrderEntity find(String token) {
      return repository.findByTrackingToken(token)
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
}
