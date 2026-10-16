package com.luthier.notification.listeners;

import com.luthier.customer.CustomerQueries;
import com.luthier.customer.CustomerQueries.CustomerSummary;
import com.luthier.customer.CustomerAnonymized;
import com.luthier.notification.domain.entities.NotificationEntity;
import com.luthier.notification.domain.enums.Channel;
import com.luthier.notification.domain.repositories.NotificationRepository;
import com.luthier.notification.services.WhatsAppSender;
import com.luthier.notification.services.impl.WhatsAppProperties;
import com.luthier.security.CustomerAccessCodes;
import com.luthier.security.WorkshopDirectory;
import com.luthier.service.QuoteApprovedByCustomer;
import com.luthier.service.ServiceOrderOpened;
import com.luthier.shared.errors.BadRequestException;
import com.luthier.shared.errors.NotFoundException;
import com.luthier.shared.tenancy.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Acesso do cliente pelo WhatsApp:
 *  - OS aberta → mensagem "OS aberta" com o link de acompanhamento da OS (portal, sem login);
 *  - orçamento aprovado pelo cliente no link → código de acesso à área do cliente
 *    (XXXX-XXXX, reutilizável por 90 dias; o código anterior deixa de valer);
 *  - balcão → "Enviar código de acesso" na ficha do cliente gera e envia um novo código;
 *  - cliente anonimizado (LGPD) → todos os códigos são revogados.
 * O código nunca é gravado em NOTIFICATION_TB (só aparece mascarado).
 */
@Component
public class CustomerAccessListeners {

  public static final Duration CODE_TTL = Duration.ofDays(90);

  private final Handlers handlers;

  public CustomerAccessListeners(Handlers handlers) {
    this.handlers = handlers;
  }

  @Async
  @TransactionalEventListener
  public void on(ServiceOrderOpened event) {
    TenantContext.runAs(event.tenantId(), () -> handlers.orderOpened(event));
  }

  @Async
  @TransactionalEventListener
  public void on(QuoteApprovedByCustomer event) {
    TenantContext.runAs(event.tenantId(), () -> handlers.quoteApproved(event));
  }

  @Async
  @TransactionalEventListener
  public void on(CustomerAnonymized event) {
    TenantContext.runAs(event.tenantId(), () -> handlers.revokeCodes(event));
  }

  @Component
  public static class Handlers {

    private static final Logger log = LoggerFactory.getLogger(Handlers.class);

    private final CustomerQueries customers;
    private final WorkshopDirectory workshops;
    private final CustomerAccessCodes accessCodes;
    private final WhatsAppSender whatsApp;
    private final WhatsAppProperties props;
    private final NotificationRepository notifications;
    private final MeterRegistry metrics;
    private final String frontendUrl;

    Handlers(CustomerQueries customers, WorkshopDirectory workshops,
        CustomerAccessCodes accessCodes, WhatsAppSender whatsApp, WhatsAppProperties props,
        NotificationRepository notifications, MeterRegistry metrics,
        @Value("${luthieria.frontend-url}") String frontendUrl) {
      this.customers = customers;
      this.workshops = workshops;
      this.accessCodes = accessCodes;
      this.whatsApp = whatsApp;
      this.props = props;
      this.notifications = notifications;
      this.metrics = metrics;
      this.frontendUrl = frontendUrl;
    }

    @Transactional
    public void orderOpened(ServiceOrderOpened e) {
      var customer = customers.findCustomer(e.customerId()).orElse(null);
      var workshop = workshops.find(e.tenantId()).orElse(null);
      if (customer == null || customer.phone() == null || workshop == null) return;

      var link = frontendUrl + "/acompanhar/" + e.trackingToken();
      var firstName = customer.name().split("\\s+")[0];
      send(customer, "OS #" + e.orderNumber() + " · OS aberta", props.templates().osAberta(),
          List.of(firstName, String.valueOf(e.orderNumber()), workshop.name(), link), null,
          "Olá %s! Sua OS #%d na %s foi aberta. Acompanhe e aprove orçamentos em %s"
              .formatted(firstName, e.orderNumber(), workshop.name(), link), e.orderId());
    }

    /** Aprovou o orçamento pelo link: ganha acesso à área do cliente para acompanhar tudo. */
    @Transactional
    public void quoteApproved(QuoteApprovedByCustomer e) {
      customers.findCustomer(e.customerId())
          .filter(c -> c.phone() != null)
          .ifPresent(c -> sendCode(c, e.orderId()));
    }

    /** Pedido do balcão (chamada síncrona, já dentro do tenant do atendente). */
    @Transactional
    public NotificationEntity resendCode(UUID customerId) {
      var customer = customers.findCustomer(customerId)
          .orElseThrow(() -> new NotFoundException("Customer not found!"));
      if (customer.phone() == null) {
        throw new BadRequestException("Cliente sem telefone cadastrado");
      }
      return sendCode(customer, null);
    }

    @Transactional
    public void revokeCodes(CustomerAnonymized e) {
      accessCodes.revokeAll(e.tenantId(), e.customerId());
    }

    private NotificationEntity sendCode(CustomerSummary customer, UUID orderId) {
      var code = accessCodes.issue(TenantContext.require(), customer.id(), customer.phone(),
          CODE_TTL);
      if (!whatsApp.isEnabled() && props.logCodes()) {
        log.warn("[DEV] Código de acesso do cliente (tel. final {}): {}",
            customer.phone().substring(customer.phone().length() - 4), code);
      }
      return send(customer, "Código de acesso", props.templates().codigoAcesso(), List.of(code),
          code, "Seu código de acesso à área do cliente é ••••-••••. Use-o sempre que quiser"
              + " acompanhar suas OS (válido por " + CODE_TTL.toDays() + " dias).", orderId);
    }

    /** Envia pela API e registra; "storedBody" é o texto SEM segredos que fica no histórico. */
    private NotificationEntity send(CustomerSummary customer, String subject, String template,
        List<String> params, String button, String storedBody, UUID orderId) {
      var n = NotificationEntity.of(Channel.WHATSAPP, customer.phone(), subject, storedBody, null,
          orderId != null ? "service_order" : "customer", orderId != null ? orderId : customer.id())
          .forCustomer(customer.id());
      if (!whatsApp.isEnabled()) {
        n.markFailed("WhatsApp Cloud API não configurada");
      } else {
        try {
          whatsApp.sendTemplate(customer.phone(), template, params, button);
          n.markSent();
        } catch (RuntimeException ex) {
          log.warn("Falha ao enviar WhatsApp ({}): {}", template, ex.getMessage());
          n.markFailed(ex.getMessage());
        }
      }
      metrics.counter("luthieria.notifications", "channel", "whatsapp_api",
          "status", n.getStatus().getValue()).increment();
      return notifications.save(n);
    }
  }
}
