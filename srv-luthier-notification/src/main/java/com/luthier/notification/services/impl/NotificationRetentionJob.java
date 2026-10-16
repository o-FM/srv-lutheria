package com.luthier.notification.services.impl;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** LGPD (minimização): notificações não são guardadas além do necessário. */
@Component
class NotificationRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);

  private final com.luthier.notification.domain.repositories.NotificationRepository repository;
  private final Duration retention;

  NotificationRetentionJob(
      com.luthier.notification.domain.repositories.NotificationRepository repository,
      @Value("${luthieria.retention.notification-days:365}") int days) {
    this.repository = repository;
    this.retention = Duration.ofDays(days);
  }

  /** Job de plataforma: sem tenant no contexto, o delete vale para todas as luthierias. */
  @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
  @Transactional
  public void purge() {
    int removed = repository.deleteOlderThan(Instant.now().minus(retention));
    log.info("Retenção: {} notificações removidas", removed);
  }
}
