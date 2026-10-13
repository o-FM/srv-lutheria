package com.luthier.jobs;

import com.luthier.domain.repositories.PasswordResetTokenRepository;
import com.luthier.domain.repositories.RefreshTokenRepository;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Limpa tokens expirados há mais de 7 dias. Requer @EnableScheduling (ver Application). */
@Component
public class TokenCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);

  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordResetTokenRepository resetTokenRepository;

  public TokenCleanupJob(RefreshTokenRepository refreshTokenRepository,
      PasswordResetTokenRepository resetTokenRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.resetTokenRepository = resetTokenRepository;
  }

  @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
  @Transactional
  public void purge() {
    var cutoff = Instant.now().minus(Duration.ofDays(7));
    int refresh = refreshTokenRepository.deleteExpiredBefore(cutoff);
    int reset = resetTokenRepository.deleteExpiredBefore(cutoff);
    log.info("Limpeza de tokens: {} refresh, {} reset removidos", refresh, reset);
  }
}

