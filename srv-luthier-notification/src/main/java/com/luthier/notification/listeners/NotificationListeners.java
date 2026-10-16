package com.luthier.notification.listeners;

import com.luthier.customer.CustomerAnonymized;
import com.luthier.customer.CustomerQueries;
import com.luthier.notification.domain.entities.NotificationEntity;
import com.luthier.notification.domain.repositories.NotificationRepository;
import com.luthier.notification.services.EmailSender;
import com.luthier.notification.services.impl.Notifier;
import com.luthier.product.StockLow;
import com.luthier.security.PasswordResetRequested;
import com.luthier.security.TenantRegistered;
import com.luthier.service.ServiceOrderStatusChanged;
import com.luthier.shared.tenancy.TenantContext;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ouve eventos públicos dos outros módulos. Listeners assíncronos rodam após o commit de quem
 * publicou, com entrega garantida pelo outbox do Modulith; o tenant vem no evento e é aplicado
 * ANTES da transação (a sessão do Hibernate fixa o tenant na abertura).
 */
@Component
public class NotificationListeners {

  private static final Logger log = LoggerFactory.getLogger(NotificationListeners.class);

  private final Handlers handlers;
  private final EmailSender emailSender;
  private final String frontendUrl;

  public NotificationListeners(Handlers handlers, EmailSender emailSender,
      @Value("${luthieria.frontend-url}") String frontendUrl) {
    this.handlers = handlers;
    this.emailSender = emailSender;
    this.frontendUrl = frontendUrl;
  }

  @Async
  @TransactionalEventListener
  public void on(ServiceOrderStatusChanged event) {
    TenantContext.runAs(event.tenantId(), () -> handlers.orderStatusChanged(event));
  }

  @Async
  @TransactionalEventListener
  public void on(TenantRegistered event) {
    TenantContext.runAs(event.tenantId(), () -> handlers.welcome(event));
  }

  @Async
  @TransactionalEventListener
  public void on(CustomerAnonymized event) {
    TenantContext.runAs(event.tenantId(), () -> handlers.scrubCustomer(event));
  }

  @Async
  @TransactionalEventListener
  public void on(StockLow event) {
    TenantContext.runAs(event.tenantId(), () -> handlers.stockLow(event));
  }

  /**
   * Síncrono e SEM outbox de propósito: o evento carrega o token bruto de redefinição, que não
   * pode ser serializado na tabela event_publication. Também não é registrado em NOTIFICATION_TB.
   */
  @EventListener
  public void on(PasswordResetRequested event) {
    var link = frontendUrl + "/redefinir-senha#token=" + event.token();
    try {
      emailSender.send(event.email(), "Redefinição de senha", """
          Olá%s,

          Recebemos um pedido para redefinir a sua senha. Use o link abaixo (válido até %s UTC):
          %s

          Se não foi você, ignore este e-mail: sua senha continua a mesma.
          """.formatted(event.name() == null ? "" : " " + event.name(), event.expiresAt(), link));
    } catch (RuntimeException e) {
      // Resposta do /auth/forgot-password é sempre 202; a falha fica só no log
      log.warn("Falha ao enviar e-mail de redefinição de senha: {}", e.getMessage());
    }
  }

  @Component
  public static class Handlers {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(
        Locale.of("pt", "BR"));

    private final Notifier notifier;
    private final CustomerQueries customers;
    private final NotificationRepository repository;
    private final String frontendUrl;

    Handlers(Notifier notifier, CustomerQueries customers, NotificationRepository repository,
        @Value("${luthieria.frontend-url}") String frontendUrl) {
      this.notifier = notifier;
      this.repository = repository;
      this.customers = customers;
      this.frontendUrl = frontendUrl;
    }

    @Transactional
    public void orderStatusChanged(ServiceOrderStatusChanged e) {
      var message = message(e);
      if (message == null) {
        return;
      }
      var customer = customers.findCustomer(e.customerId()).orElse(null);
      if (customer == null) {
        return;
      }
      var firstName = customer.name().split("\\s+")[0];
      var text = "Olá, " + firstName + "! " + message;
      var subject = "OS #" + e.orderNumber() + " · " + subjectFor(e.to());

      if (customer.email() != null) {
        notifier.email(customer.email(), subject, text, "service_order", e.orderId(),
            e.customerId());
      }
      if (customer.phone() != null) {
        notifier.whatsapp(customer.phone(), subject, text, "service_order", e.orderId(),
            e.customerId());
      }
    }

    @Transactional
    public void welcome(TenantRegistered e) {
      notifier.email(e.ownerEmail(), "Bem-vindo(a) à Luthieria!", """
          Olá %s,

          A oficina "%s" foi cadastrada com sucesso. Acesse %s para cadastrar clientes,
          instrumentos, estoque e abrir a primeira ordem de serviço.
          """.formatted(e.ownerName(), e.tenantName(), frontendUrl), "tenant", e.tenantId(),
          null);
    }

    @Transactional
    public void scrubCustomer(CustomerAnonymized e) {
      repository.findAllByCustomerId(e.customerId()).forEach(NotificationEntity::scrub);
    }

    @Transactional
    public void stockLow(StockLow e) {
      notifier.internal("Estoque baixo: " + e.name(),
          "Saldo atual " + e.currentQuantity() + " (mínimo " + e.minQuantity() + ").",
          "product", e.productId());
    }

    private String message(ServiceOrderStatusChanged e) {
      var link = frontendUrl + "/acompanhar/" + e.trackingToken();
      return switch (e.to()) {
        case "aguardando_aprovacao" -> "O orçamento da OS #%d ficou em %s. Veja os detalhes e aprove em 1 toque: %s"
            .formatted(e.orderNumber(), brl(e.grandTotal()), link);
        case "em_execucao" -> "Orçamento aprovado! Seu instrumento já está na bancada. Acompanhe: %s"
            .formatted(link);
        case "pronto" -> e.balance().signum() > 0
            ? "Seu instrumento está pronto para retirada! Saldo a pagar: %s. %s"
                .formatted(brl(e.balance()), link)
            : "Seu instrumento está pronto para retirada! %s".formatted(link);
        case "entregue" -> "Obrigado pela confiança! Qualquer ajuste, é só chamar.";
        case "cancelado" -> "A OS #%d foi cancelada. Fale com a gente se tiver dúvidas."
            .formatted(e.orderNumber());
        default -> null;
      };
    }

    private static String subjectFor(String status) {
      return switch (status) {
        case "aguardando_aprovacao" -> "Orçamento disponível";
        case "em_execucao" -> "Orçamento aprovado";
        case "pronto" -> "Pronto para retirada";
        case "entregue" -> "Entregue";
        case "cancelado" -> "Cancelada";
        default -> status;
      };
    }

    private static String brl(BigDecimal value) {
      synchronized (BRL) {
        return BRL.format(value);
      }
    }
  }
}
