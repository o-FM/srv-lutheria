package com.luthier.security.services.impl;

import com.luthier.security.CustomerAccessCodes;
import com.luthier.security.WorkshopDirectory;
import com.luthier.security.domain.entities.CustomerAccessCodeEntity;
import com.luthier.security.domain.errors.InvalidTokenException;
import com.luthier.security.domain.records.CustomerLoginRequest;
import com.luthier.security.domain.records.TokenResponse;
import com.luthier.security.domain.repositories.CustomerAccessCodeRepository;
import com.luthier.security.domain.repositories.TenantRepository;
import com.luthier.security.services.CustomerAuthService;
import com.luthier.security.services.TokenService;
import com.luthier.shared.audit.AuditEvent;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Área do cliente com login só pelo código. O código é a credencial inteira, então:
 * - é gerado com SecureRandom (≈ 6,6 × 10¹¹ combinações) e único na plataforma;
 * - guardamos HMAC-SHA256 com a chave secreta do servidor (busca direta pelo hash; um vazamento do
 *   banco não permite testar códigos offline);
 * - a rota tem rate limit por IP; cada código novo revoga os anteriores do cliente.
 */
@Service
public class CustomerAccessServiceImpl
    implements CustomerAccessCodes, CustomerAuthService, WorkshopDirectory {

  private final CustomerAccessCodeRepository codes;
  private final TenantRepository tenants;
  private final TokenService tokenService;
  private final ApplicationEventPublisher events;
  private final SecretKey key;

  public CustomerAccessServiceImpl(CustomerAccessCodeRepository codes, TenantRepository tenants,
      TokenService tokenService, ApplicationEventPublisher events, SecretKey jwtKey) {
    this.codes = codes;
    this.tenants = tenants;
    this.tokenService = tokenService;
    this.events = events;
    this.key = jwtKey;
  }

  // ---------------- CustomerAccessCodes (API pública) ----------------

  @Override
  @Transactional
  public String issue(UUID tenantId, UUID customerId, String phoneDigits, Duration ttl) {
    var now = Instant.now();
    codes.revokeAll(tenantId, customerId, now);
    String code;
    String hash;
    do {
      code = AccessCodeFormat.generate();
      hash = hmac(code);
    } while (codes.existsByCodeHash(hash));
    codes.save(CustomerAccessCodeEntity.issue(tenantId, customerId, phoneDigits, hash, now.plus(ttl)));
    return AccessCodeFormat.display(code);
  }

  @Override
  @Transactional
  public void revokeAll(UUID tenantId, UUID customerId) {
    codes.revokeAll(tenantId, customerId, Instant.now());
  }

  // ---------------- WorkshopDirectory (API pública) ----------------

  @Override
  @Transactional(readOnly = true)
  public Optional<Workshop> find(UUID tenantId) {
    return tenants.findById(tenantId).map(t -> new Workshop(t.getId(), t.getName(), t.getSlug()));
  }

  // ---------------- login da área do cliente ----------------

  @Override
  @Transactional
  public TokenResponse login(CustomerLoginRequest request) {
    var now = Instant.now();
    var canonical = AccessCodeFormat.normalize(request.code());
    var code = Optional.ofNullable(canonical)
        .flatMap(c -> codes.findByCodeHash(hmac(c)))
        .filter(c -> c.isValid(now))
        .filter(c -> tenants.isActive(c.getTenantId()))
        .orElseThrow(() -> {
          events.publishEvent(new AuditEvent(null, "CUSTOMER_LOGIN_FAILED", "customer", null, null));
          return InvalidTokenException.refresh();
        });

    code.registerLogin(now);
    events.publishEvent(new AuditEvent(code.getTenantId(), "CUSTOMER_LOGIN_SUCCESS", "customer",
        code.getCustomerId().toString(), null));
    return new TokenResponse(
        tokenService.generateForCustomer(code.getTenantId(), code.getCustomerId(), code.getPhone()),
        null, "Bearer", tokenService.customerExpirationInSeconds());
  }

  private String hmac(String canonicalCode) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(key);
      return HexFormat.of().formatHex(
          mac.doFinal(("cliente|" + canonicalCode).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC indisponível", e);
    }
  }
}
