package com.luthier.security.jobs;

import com.luthier.security.domain.repositories.AuditLogRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Trilha de auditoria guardada por prazo definido (padrão 5 anos) e depois removida. */
@Component
public class AuditRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

  private final AuditLogRepository repository;
  private final Duration retention;

  public AuditRetentionJob(AuditLogRepository repository,
      @Value("${luthieria.retention.audit-days:1825}") int days) {
    this.repository = repository;
    this.retention = Duration.ofDays(days);
  }

  @Scheduled(cron = "0 45 3 * * *", zone = "America/Sao_Paulo")
  @Transactional
  public void purge() {
    int removed = repository.deleteOlderThan(Instant.now().minus(retention));
    log.info("Retenção: {} registros de auditoria removidos", removed);
  }
}
