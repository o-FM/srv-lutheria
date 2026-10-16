package com.luthier.security.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma luthieria (tenant). Todas compartilham o mesmo banco; o isolamento é feito pela coluna
 * tenant_id nas tabelas de negócio. Esta tabela em si é de plataforma (não tem tenant_id).
 */
@Entity
@Table(name = "TENANT_TB")
public class TenantEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 120)
  private String name;

  /** Identificador legível e único (ex.: "oficina-do-armando"), útil para URLs e suporte. */
  @Column(nullable = false, unique = true, length = 60)
  private String slug;

  /** CNPJ ou CPF do dono, só dígitos. Opcional no cadastro inicial. */
  @Column(length = 14)
  private String document;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected TenantEntity() {}

  public static TenantEntity newTenant(String name, String slug, String document) {
    var t = new TenantEntity();
    t.name = name;
    t.slug = slug;
    t.document = document;
    t.active = true;
    t.createdAt = Instant.now();
    return t;
  }

  public UUID getId() { return id; }
  public String getName() { return name; }
  public String getSlug() { return slug; }
  public String getDocument() { return document; }
  public boolean isActive() { return active; }
  public Instant getCreatedAt() { return createdAt; }

  public void activate() { this.active = true; }
  public void deactivate() { this.active = false; }
}
