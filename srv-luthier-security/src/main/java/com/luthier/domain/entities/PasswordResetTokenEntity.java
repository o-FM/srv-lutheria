package com.luthier.domain.entities;

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
@Table(name = "PASSWORD_RESET_TOKEN_TB", indexes = {
    @Index(name = "idx_reset_user", columnList = "user_id")
})
public class PasswordResetTokenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
  private String tokenHash;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PasswordResetTokenEntity() {}

  public static PasswordResetTokenEntity issue(UUID userId, String tokenHash, Instant expiresAt) {
    var t = new PasswordResetTokenEntity();
    t.userId = userId;
    t.tokenHash = tokenHash;
    t.expiresAt = expiresAt;
    t.createdAt = Instant.now();
    return t;
  }

  public UUID getUserId() { return userId; }

  public boolean isUsed() { return usedAt != null; }
  public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }

  public void markUsed(Instant now) { this.usedAt = now; }
}
