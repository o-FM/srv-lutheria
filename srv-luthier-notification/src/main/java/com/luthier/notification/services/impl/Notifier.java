package com.luthier.notification.services.impl;

import com.luthier.notification.domain.entities.NotificationEntity;
import com.luthier.notification.domain.enums.Channel;
import com.luthier.notification.domain.repositories.NotificationRepository;
import com.luthier.notification.services.EmailSender;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cria, envia e registra notificações dentro do tenant atual. */
@Component
public class Notifier {

  private static final Logger log = LoggerFactory.getLogger(Notifier.class);

  private final NotificationRepository repository;
  private final EmailSender emailSender;
  private final MeterRegistry metrics;

  public Notifier(NotificationRepository repository, EmailSender emailSender,
      MeterRegistry metrics) {
    this.repository = repository;
    this.emailSender = emailSender;
    this.metrics = metrics;
  }

  /** Falha de envio não propaga: fica registrada como "falhou" para reenvio/consulta. */
  @Transactional
  public NotificationEntity email(String to, String subject, String body, String relatedType,
      UUID relatedId) {
    var n = NotificationEntity.of(Channel.EMAIL, to, subject, body, null, relatedType, relatedId);
    try {
      if (emailSender.send(to, subject, body)) {
        n.markSent();
      } else {
        n.markFailed("SMTP não configurado");
      }
    } catch (RuntimeException e) {
      log.warn("Falha ao enviar e-mail ({}): {}", relatedType, e.getMessage());
      n.markFailed(e.getMessage());
    }
    return save(n);
  }

  /** Gera a mensagem pronta e o link wa.me; o balcão envia em 1 toque e marca como enviada. */
  @Transactional
  public NotificationEntity whatsapp(String phoneDigits, String subject, String text,
      String relatedType, UUID relatedId) {
    var url = "https://wa.me/55" + phoneDigits + "?text="
        + URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    return save(NotificationEntity.of(Channel.WHATSAPP, phoneDigits, subject, text, url,
        relatedType, relatedId));
  }

  @Transactional
  public NotificationEntity internal(String subject, String body, String relatedType,
      UUID relatedId) {
    return save(NotificationEntity.of(Channel.INTERNAL, null, subject, body, null, relatedType,
        relatedId));
  }

  private NotificationEntity save(NotificationEntity n) {
    metrics.counter("luthieria.notifications", "channel", n.getChannel().getValue(),
        "status", n.getStatus().getValue()).increment();
    return repository.save(n);
  }
}
