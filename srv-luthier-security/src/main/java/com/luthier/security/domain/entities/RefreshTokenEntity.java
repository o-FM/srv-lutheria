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

@Entity
@Table(name = "REFRESH_TOKEN_TB", indexes = {
    @Index(name = "idx_refresh_family", columnList = "family_id"),
    @Index(name = "idx_refresh_user", columnList = "user_id")
})
public class RefreshTokenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
  private String tokenHash;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "family_id", nullable = false, updatable = false)
  private UUID familyId;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected RefreshTokenEntity() {}

  public static RefreshTokenEntity issue(UUID userId, String tokenHash, UUID familyId,
      Instant expiresAt) {
    var t = new RefreshTokenEntity();
    t.userId = userId;
    t.tokenHash = tokenHash;
    t.familyId = familyId;
    t.expiresAt = expiresAt;
    t.createdAt = Instant.now();
    return t;
  }

  public UUID getUserId() { return userId; }
  public UUID getFamilyId() { return familyId; }

  public boolean isRevoked() { return revokedAt != null; }
  public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }

  public void revoke(Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
    }
  }
}
