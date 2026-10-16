package com.luthier.notification.listeners;

import com.luthier.customer.CustomerQueries;
import com.luthier.customer.CustomerQueries.CustomerSummary;
import com.luthier.notification.domain.entities.NotificationEntity;
import com.luthier.notification.domain.enums.Channel;
import com.luthier.notification.domain.repositories.NotificationRepository;
import com.luthier.notification.services.WhatsAppSender;
import com.luthier.notification.services.impl.WhatsAppProperties;
import com.luthier.security.CustomerAccessCodeRequested;
import com.luthier.security.CustomerAccessCodes;
import com.luthier.security.WorkshopDirectory;
import com.luthier.service.ServiceOrderOpened;
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
 * Acesso do cliente pelo WhatsApp, 100% automático:
 *  - OS aberta → mensagem "OS aberta" com o link da área do cliente + código de acesso (72h);
 *  - "receber novo código" na tela de login → novo código (10 min).
 * O código nunca é gravado em NOTIFICATION_TB (só aparece mascarado).
 */
@Component
public class CustomerAccessListeners {

  static final Duration OS_CODE_TTL = Duration.ofHours(72);
  static final Duration RESEND_CODE_TTL = Duration.ofMinutes(10);

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
  public void on(CustomerAccessCodeRequested event) {
    TenantContext.runAs(event.tenantId(), () -> handlers.codeRequested(event));
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

      var link = frontendUrl + "/cliente/" + workshop.slug();
      var firstName = customer.name().split("\\s+")[0];
      send(customer, "OS #" + e.orderNumber() + " · OS aberta", props.templates().osAberta(),
          List.of(firstName, String.valueOf(e.orderNumber()), workshop.name(), link), null,
          "Olá %s! Sua OS #%d na %s foi aberta. Acompanhe e aprove orçamentos em %s"
              .formatted(firstName, e.orderNumber(), workshop.name(), link), e.orderId());

      sendCode(customer, OS_CODE_TTL, e.orderId());
    }

    @Transactional
    public void codeRequested(CustomerAccessCodeRequested e) {
      // Telefone que não é de cliente: não faz nada (e a API respondeu igual)
      customers.findByPhone(e.phoneDigits()).ifPresent(c -> sendCode(c, RESEND_CODE_TTL, null));
    }

    private void sendCode(CustomerSummary customer, Duration ttl, UUID orderId) {
      var code = accessCodes.issue(TenantContext.require(), customer.id(), customer.phone(), ttl);
      if (!whatsApp.isEnabled() && props.logCodes()) {
        log.warn("[DEV] Código de acesso do cliente (tel. final {}): {}",
            customer.phone().substring(customer.phone().length() - 4), code);
      }
      send(customer, "Código de acesso", props.templates().codigoAcesso(), List.of(code), code,
          "•••••• é o seu código de acesso (válido por " + describe(ttl) + ").", orderId);
    }

    /** Envia pela API e registra; "storedBody" é o texto SEM segredos que fica no histórico. */
    private void send(CustomerSummary customer, String subject, String template,
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
      notifications.save(n);
    }

    private static String describe(Duration ttl) {
      return ttl.toHours() >= 1 ? ttl.toHours() + "h" : ttl.toMinutes() + " min";
    }
  }
}
