package com.luthier.shared.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Base de toda entidade de negócio que pertence a uma luthieria.
 *
 * - tenant_id é preenchido pelo Hibernate (nunca pelo código) e não muda depois do INSERT.
 * - @Version dá lock otimista: duas edições concorrentes do mesmo registro não se sobrescrevem.
 */
@MappedSuperclass
public abstract class TenantScopedEntity {

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  private long version;

  /**
   * Sem tenant no contexto não há luthieria dona da linha (SH-01). Isso vale também para o escopo
   * de plataforma, que é só de leitura. A FK para tenant_tb (APP-03) barraria o INSERT depois, mas
   * aqui o erro sai antes, e com uma causa legível.
   */
  @PrePersist
  protected void onCreate() {
    if (TenantContext.current().isEmpty()) {
      throw new IllegalStateException("Sem tenant no contexto: dados de negócio só podem ser "
          + "gravados dentro de uma luthieria (TenantContext.runAs)");
    }
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getTenantId() { return tenantId; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }
}
