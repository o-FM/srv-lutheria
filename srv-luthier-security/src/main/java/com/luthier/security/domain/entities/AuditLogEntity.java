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
import org.hibernate.annotations.Immutable;

/**
 * Trilha de auditoria (append-only). tenant_id é coluna simples, sem @TenantId: eventos de
 * plataforma (ex.: login com e-mail inexistente) não têm luthieria. A leitura filtra por tenant
 * explicitamente.
 */
@Entity
@Immutable
@Table(name = "AUDIT_LOG_TB", indexes =
    @Index(name = "idx_audit_tenant_created", columnList = "tenant_id, created_at"))
public class AuditLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", updatable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 254, updatable = false)
  private String actor;

  @Column(nullable = false, length = 60, updatable = false)
  private String action;

  @Column(name = "resource_type", length = 40, updatable = false)
  private String resourceType;

  @Column(name = "resource_id", length = 64, updatable = false)
  private String resourceId;

  @Column(length = 500, updatable = false)
  private String details;

  @Column(length = 45, updatable = false)
  private String ip;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AuditLogEntity() {}

  public static AuditLogEntity of(UUID tenantId, String actor, String action, String resourceType,
      String resourceId, String details, String ip) {
    var a = new AuditLogEntity();
    a.tenantId = tenantId;
    a.actor = actor;
    a.action = action;
    a.resourceType = resourceType;
    a.resourceId = resourceId;
    a.details = details == null ? null : details.substring(0, Math.min(details.length(), 500));
    a.ip = ip;
    a.createdAt = Instant.now();
    return a;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public String getActor() { return actor; }
  public String getAction() { return action; }
  public String getResourceType() { return resourceType; }
  public String getResourceId() { return resourceId; }
  public String getDetails() { return details; }
  public String getIp() { return ip; }
  public Instant getCreatedAt() { return createdAt; }
}
