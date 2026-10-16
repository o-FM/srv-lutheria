package com.luthier.security.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Código de acesso da área do cliente. Só o HMAC do código é guardado. Uso único, com prazo e
 * limite de tentativas. tenant_id é coluna simples: o login acontece antes de haver tenant.
 */
@Entity
@Table(name = "CUSTOMER_ACCESS_CODE_TB", indexes =
    @Index(name = "idx_access_code_tenant_phone", columnList = "tenant_id, phone"))
public class CustomerAccessCodeEntity {

  public static final int MAX_ATTEMPTS = 5;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(nullable = false, length = 11, updatable = false)
  private String phone;

  @Column(name = "code_hash", nullable = false, length = 64, updatable = false)
  private String codeHash;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CustomerAccessCodeEntity() {}

  public static CustomerAccessCodeEntity issue(UUID tenantId, UUID customerId, String phone,
      String codeHash, Instant expiresAt) {
    var c = new CustomerAccessCodeEntity();
    c.tenantId = tenantId;
    c.customerId = customerId;
    c.phone = phone;
    c.codeHash = codeHash;
    c.expiresAt = expiresAt;
    c.createdAt = Instant.now();
    return c;
  }

  public boolean isUsable(Instant now) {
    return usedAt == null && attempts < MAX_ATTEMPTS && expiresAt.isAfter(now);
  }

  public void registerFailedAttempt() { attempts++; }

  public void markUsed(Instant now) { usedAt = now; }

  public UUID getTenantId() { return tenantId; }
  public UUID getCustomerId() { return customerId; }
  public String getPhone() { return phone; }
  public String getCodeHash() { return codeHash; }
}
