package com.luthier.notification.services.impl;

import com.luthier.notification.domain.repositories.NotificationRepository;
import com.luthier.shared.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * LGPD (minimização): notificações não são guardadas além do necessário.
 *
 * Job de plataforma: o expurgo vale para todas as luthierias, então roda no escopo de plataforma
 * pedido explicitamente. Sem ele, o resolver cai no modo restrito e o DELETE não apaga nada
 * (SH-01, NOT-03). O escopo precisa estar aberto ANTES da transação, porque a sessão do Hibernate
 * fixa o tenant na abertura. Por isso existe o bean interno {@link Purger}.
 */
@Component
class NotificationRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);

  private final Purger purger;
  private final Duration retention;

  NotificationRetentionJob(Purger purger,
      @Value("${luthieria.retention.notification-days:365}") int days) {
    this.purger = purger;
    this.retention = Duration.ofDays(days);
  }

  @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
  public void purge() {
    var cutoff = Instant.now().minus(retention);
    int removed = TenantContext.callAsPlatform(() -> purger.deleteOlderThan(cutoff));
    log.info("Retenção: {} notificações removidas", removed);
  }

  @Component
  static class Purger {

    private final NotificationRepository repository;

    Purger(NotificationRepository repository) {
      this.repository = repository;
    }

    @Transactional
    public int deleteOlderThan(Instant cutoff) {
      return repository.deleteOlderThan(cutoff);
    }
  }
}
