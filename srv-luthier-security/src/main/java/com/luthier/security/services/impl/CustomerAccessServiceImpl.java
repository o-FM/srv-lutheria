package com.luthier.security.services.impl;

import com.luthier.security.CustomerAccessCodeRequested;
import com.luthier.security.CustomerAccessCodes;
import com.luthier.security.WorkshopDirectory;
import com.luthier.security.domain.entities.CustomerAccessCodeEntity;
import com.luthier.security.domain.errors.InvalidTokenException;
import com.luthier.security.domain.records.CustomerCodeRequest;
import com.luthier.security.domain.records.CustomerLoginRequest;
import com.luthier.security.domain.records.TokenResponse;
import com.luthier.security.domain.repositories.CustomerAccessCodeRepository;
import com.luthier.security.domain.repositories.TenantRepository;
import com.luthier.security.services.CustomerAuthService;
import com.luthier.security.services.TokenService;
import com.luthier.shared.audit.AuditEvent;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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
 * Códigos de 6 dígitos têm pouca entropia, então:
 * - guardamos HMAC-SHA256 com a chave secreta do servidor (um vazamento do banco não permite
 *   testar os 10⁶ códigos offline);
 * - cada código aceita no máximo 5 tentativas e expira (72h no envio da OS, 10 min no reenvio);
 * - a rota tem rate limit por IP.
 */
@Service
public class CustomerAccessServiceImpl
    implements CustomerAccessCodes, CustomerAuthService, WorkshopDirectory {

  private static final SecureRandom RANDOM = new SecureRandom();

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
    codes.invalidatePending(tenantId, customerId, now);
    var code = "%06d".formatted(RANDOM.nextInt(1_000_000));
    codes.save(CustomerAccessCodeEntity.issue(tenantId, customerId, phoneDigits,
        hmac(tenantId, phoneDigits, code), now.plus(ttl)));
    return code;
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
  public void requestCode(CustomerCodeRequest request) {
    tenants.findBySlugAndActiveTrue(request.workshop().strip())
        .ifPresent(t -> events.publishEvent(
            new CustomerAccessCodeRequested(t.getId(), digits(request.phone()))));
  }

  /** noRollbackFor: a tentativa errada precisa ser contada mesmo com o 401. */
  @Override
  @Transactional(noRollbackFor = InvalidTokenException.class)
  public TokenResponse login(CustomerLoginRequest request) {
    var now = Instant.now();
    var tenant = tenants.findBySlugAndActiveTrue(request.workshop().strip())
        .orElseThrow(InvalidTokenException::refresh);
    var phone = digits(request.phone());
    var expected = hmac(tenant.getId(), phone, request.code());

    var open = codes.findOpenForUpdate(tenant.getId(), phone, now).stream()
        .filter(c -> c.isUsable(now))
        .toList();
    var match = open.stream()
        .filter(c -> MessageDigest.isEqual(c.getCodeHash().getBytes(StandardCharsets.US_ASCII),
            expected.getBytes(StandardCharsets.US_ASCII)))
        .findFirst();

    if (match.isEmpty()) {
      open.forEach(CustomerAccessCodeEntity::registerFailedAttempt);
      events.publishEvent(new AuditEvent(tenant.getId(), "CUSTOMER_LOGIN_FAILED", "customer",
          null, "telefone final " + phone.substring(phone.length() - 4)));
      throw InvalidTokenException.refresh();
    }

    var code = match.get();
    code.markUsed(now);
    events.publishEvent(new AuditEvent(tenant.getId(), "CUSTOMER_LOGIN_SUCCESS", "customer",
        code.getCustomerId().toString(), null));
    return new TokenResponse(
        tokenService.generateForCustomer(tenant.getId(), code.getCustomerId(), phone),
        null, "Bearer", tokenService.customerExpirationInSeconds());
  }

  private String hmac(UUID tenantId, String phone, String code) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(key);
      var digest = mac.doFinal((tenantId + "|" + phone + "|" + code)
          .getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC indisponível", e);
    }
  }

  private static String digits(String phone) {
    return phone.replaceAll("\\D", "");
  }
}
