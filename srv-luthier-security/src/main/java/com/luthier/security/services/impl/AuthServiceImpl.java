package com.luthier.security.services.impl;

import com.luthier.security.configs.SegurancaProperties;
import com.luthier.security.domain.entities.RefreshTokenEntity;
import com.luthier.security.domain.entities.UserEntity;
import com.luthier.security.domain.errors.InvalidTokenException;
import com.luthier.security.domain.records.LoginRequest;
import com.luthier.security.domain.records.RefreshRequest;
import com.luthier.security.domain.records.TokenResponse;
import com.luthier.security.domain.repositories.RefreshTokenRepository;
import com.luthier.security.domain.repositories.TenantRepository;
import com.luthier.security.domain.repositories.UserRepository;
import com.luthier.security.services.AuthService;
import com.luthier.security.services.TokenService;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import com.luthier.shared.audit.AuditEvent;
import com.luthier.shared.validation.Cpf;
import com.luthier.shared.validation.Masks;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

  private final AuthenticationManager authenticationManager;
  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final TenantRepository tenantRepository;
  private final TokenService tokenService;
  private final Duration refreshTtl;
  private final ApplicationEventPublisher events;

  public AuthServiceImpl(AuthenticationManager authenticationManager, UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository, TenantRepository tenantRepository,
      TokenService tokenService, SegurancaProperties props, ApplicationEventPublisher events) {
    this.events = events;
    this.authenticationManager = authenticationManager;
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.tenantRepository = tenantRepository;
    this.tokenService = tokenService;
    this.refreshTtl = Duration.ofDays(props.tokens().refreshDays());
  }

  /**
   * E-mail é único na plataforma. CPF é único só dentro de cada luthieria, então um CPF pode ter
   * conta em mais de uma: tentamos a senha em cada conta ativa com aquele CPF; se servir em mais de
   * uma, pedimos o e-mail para saber de qual oficina se trata.
   */
  @Override
  @Transactional
  public TokenResponse login(LoginRequest request) {
    var identifier = request.identifier().strip();
    var byEmail = identifier.contains("@");
    var cpf = byEmail ? null : Cpf.digits(identifier);
    List<String> emails = byEmail
        ? List.of(identifier.toLowerCase(java.util.Locale.ROOT))
        // Sem CPF válido não consulta nada (evita "cpf is null" casar com todos os usuários)
        : cpf == null || !Cpf.isValid(cpf) ? List.of()
        : userRepository.findAllByCpfAndActiveTrue(cpf).stream().map(UserEntity::getEmail).toList();

    List<UserEntity> matches = new ArrayList<>();
    AuthenticationException failure = new BadCredentialsException("Bad credentials");
    for (var email : emails) {
      try {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        userRepository.findByEmailIgnoreCase(email).ifPresent(matches::add);
      } catch (AuthenticationException e) {
        failure = e;
      }
    }

    if (matches.isEmpty()) {
      var tenantId = emails.isEmpty() ? null : userRepository.findByEmailIgnoreCase(emails.getFirst())
          .map(UserEntity::getTenantId).orElse(null);
      var who = byEmail ? Masks.email(identifier) : "CPF " + (cpf == null ? "inválido" : Cpf.mask(cpf));
      events.publishEvent(new AuditEvent(tenantId, "LOGIN_FAILED", "user", null,
          who + " · " + failure.getClass().getSimpleName()));
      throw failure;
    }
    if (matches.size() > 1) {
      throw new BadCredentialsException("CPF em mais de uma oficina: entre com o e-mail");
    }

    var user = matches.getFirst();
    events.publishEvent(new AuditEvent(user.getTenantId(), "LOGIN_SUCCESS", "user",
        user.getUuid().toString(), byEmail ? "e-mail" : "CPF"));

    return issue(user, UUID.randomUUID());
  }

  /**
   * Rotação com detecção de reuso:
   *  1. token desconhecido            -> 401
   *  2. token JÁ revogado (reuso!)    -> revoga a família inteira e 401
   *  3. token expirado                -> 401
   *  4. ok: revoga o atual e emite um novo na mesma família
   *
   * noRollbackFor é essencial: no caso 2 lançamos exceção, mas o revokeFamily() PRECISA
   * ser commitado. Sem isso o rollback desfaria a revogação e o ataque passaria.
   */
  @Override
  @Transactional(noRollbackFor = InvalidTokenException.class)
  public TokenResponse refresh(RefreshRequest request) {
    var now = Instant.now();

    var stored = refreshTokenRepository
        .findForUpdateByTokenHash(OpaqueTokens.hash(request.refreshToken()))
        .orElseThrow(InvalidTokenException::refresh);

    if (stored.isRevoked()) {
      refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
      throw InvalidTokenException.refresh();
    }

    if (stored.isExpired(now)) {
      throw InvalidTokenException.refresh();
    }

    // Usuário (ou luthieria) desativado depois do login não consegue renovar sessão
    var user = userRepository.findById(stored.getUserId())
        .filter(UserEntity::isActive)
        .filter(u -> tenantRepository.isActive(u.getTenantId()))
        .orElseThrow(InvalidTokenException::refresh);

    stored.revoke(now);

    return issue(user, stored.getFamilyId());
  }

  @Override
  @Transactional
  public void logout(RefreshRequest request) {
    // Idempotente: token desconhecido não gera erro (e não revela nada)
    refreshTokenRepository.findByTokenHash(OpaqueTokens.hash(request.refreshToken()))
        .ifPresent(t -> refreshTokenRepository.revokeFamily(t.getFamilyId(), Instant.now()));
  }

  @Override
  @Transactional
  public TokenResponse startSession(UserEntity user) {
    return issue(user, UUID.randomUUID());
  }

  private TokenResponse issue(UserEntity user, UUID uuid) {
    var rawRefresh = OpaqueTokens.generate();

    refreshTokenRepository.save(RefreshTokenEntity.issue(
        user.getUuid(),
        OpaqueTokens.hash(rawRefresh),
        uuid,
        Instant.now().plus(refreshTtl)));

    return new TokenResponse(
        tokenService.generate(user),
        rawRefresh,
        "Bearer",
        tokenService.expirationInSeconds());
  }
}
