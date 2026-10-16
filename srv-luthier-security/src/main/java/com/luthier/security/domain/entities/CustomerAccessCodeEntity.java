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
 * Código de acesso da área do cliente. Só o HMAC do código é guardado (índice único, usado na
 * busca do login). Pode ser usado várias vezes até expirar ou ser revogado por um código novo.
 * tenant_id é coluna simples: o login acontece antes de haver tenant.
 */
@Entity
@Table(name = "CUSTOMER_ACCESS_CODE_TB", indexes =
    @Index(name = "idx_access_code_tenant_customer", columnList = "tenant_id, customer_id"))
public class CustomerAccessCodeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(nullable = false, length = 11, updatable = false)
  private String phone;

  @Column(name = "code_hash", nullable = false, unique = true, length = 64, updatable = false)
  private String codeHash;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

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

  public boolean isValid(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  public void registerLogin(Instant now) { lastLoginAt = now; }

  public UUID getTenantId() { return tenantId; }
  public UUID getCustomerId() { return customerId; }
  public String getPhone() { return phone; }
}
