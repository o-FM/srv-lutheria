package com.luthier.security.services.impl;

import com.luthier.security.configs.SegurancaProperties;
import com.luthier.security.domain.entities.PasswordResetTokenEntity;
import com.luthier.security.domain.entities.UserEntity;
import com.luthier.security.domain.errors.InvalidTokenException;
import com.luthier.security.domain.events.PasswordResetRequested;
import com.luthier.security.domain.records.ForgotPasswordRequest;
import com.luthier.security.domain.records.ResetPasswordRequest;
import com.luthier.security.domain.repositories.PasswordResetTokenRepository;
import com.luthier.security.domain.repositories.RefreshTokenRepository;
import com.luthier.security.domain.repositories.UserRepository;
import com.luthier.security.services.PasswordRecoveryService;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {

  private final UserRepository userRepository;
  private final PasswordResetTokenRepository resetTokenRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher events;
  private final Duration resetTtl;

  public PasswordRecoveryServiceImpl(UserRepository userRepository,
      PasswordResetTokenRepository resetTokenRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      ApplicationEventPublisher events,
      SegurancaProperties props) {
    this.userRepository = userRepository;
    this.resetTokenRepository = resetTokenRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.events = events;
    this.resetTtl = Duration.ofMinutes(props.tokens().resetMinutes());
  }

  /**
   * Anti-enumeração: se o e-mail não existe (ou o usuário está inativo) simplesmente não faz nada
   * e retorna normal. O controller responde 202 nos dois casos.
   */
  @Override
  @Transactional
  public void requestReset(ForgotPasswordRequest request) {
    userRepository.findByEmailIgnoreCase(request.email().strip())
        .filter(UserEntity::isActive)
        .ifPresent(this::createAndPublish);
  }

  private void createAndPublish(UserEntity user) {
    var now = Instant.now();

    // Só o link mais recente vale
    resetTokenRepository.invalidatePending(user.getUuid(), now);

    var raw = OpaqueTokens.generate();
    var expiresAt = now.plus(resetTtl);

    resetTokenRepository.save(
        PasswordResetTokenEntity.issue(user.getUuid(), OpaqueTokens.hash(raw), expiresAt));

    events.publishEvent(new PasswordResetRequested(user.getUuid(), user.getEmail(), raw, expiresAt));
  }

  @Override
  @Transactional
  public void reset(ResetPasswordRequest request) {
    var now = Instant.now();

    var token = resetTokenRepository
        .findForUpdateByTokenHash(OpaqueTokens.hash(request.token()))
        .orElseThrow(InvalidTokenException::reset);

    if (token.isUsed() || token.isExpired(now)) {
      throw InvalidTokenException.reset();
    }

    var user = userRepository.findById(token.getUserId())
        .filter(UserEntity::isActive)
        .orElseThrow(InvalidTokenException::reset);

    user.changePassword(passwordEncoder.encode(request.newPassword()));
    token.markUsed(now);

    // Quem roubou a senha antiga não pode continuar logado com um refresh token antigo
    refreshTokenRepository.revokeAllByUser(user.getUuid(), now);
  }
}
