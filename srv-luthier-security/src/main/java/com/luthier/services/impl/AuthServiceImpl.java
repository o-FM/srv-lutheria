package com.luthier.services.impl;

import com.luthier.domain.entities.RefreshTokenEntity;
import com.luthier.domain.entities.UserEntity;
import com.luthier.domain.errors.InvalidTokenException;
import com.luthier.domain.records.LoginRequest;
import com.luthier.domain.records.RefreshRequest;
import com.luthier.domain.records.TokenResponse;
import com.luthier.domain.repositories.RefreshTokenRepository;
import com.luthier.domain.repositories.UserRepository;
import com.luthier.services.AuthService;
import com.luthier.services.TokenService;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

  private final AuthenticationManager authenticationManager;
  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final TokenService tokenService;
  private final Duration refreshTtl;

  public AuthServiceImpl(AuthenticationManager authenticationManager, UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository, TokenService tokenService, Duration refreshTtl) {
    this.authenticationManager = authenticationManager;
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.tokenService = tokenService;
    this.refreshTtl = refreshTtl;
  }

  @Override
  public TokenResponse login(LoginRequest request) {
    var email = request.email().strip();

    authenticationManager.authenticate(
        UsernamePasswordAuthenticationToken.unauthenticated(email, request.password())
    );

    var user = userRepository.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new BadCredentialsException("Bad credentials"));

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

    // Usuário desativado/removido depois do login não consegue renovar sessão
    var user = userRepository.findById(stored.getUserId())
        .filter(UserEntity::isActive)
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
