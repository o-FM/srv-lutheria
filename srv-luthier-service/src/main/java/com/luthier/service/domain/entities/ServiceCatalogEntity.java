package com.luthier.service.domain.entities;

import com.luthier.shared.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Serviço pré-configurado da luthieria (ex.: "Regulagem completa", "Troca de trastes"), usado no
 * passo 3 do wizard de OS. Cada luthieria tem o seu catálogo e os seus preços.
 */
@Entity
@Table(name = "SERVICE_CATALOG_TB", uniqueConstraints =
    @UniqueConstraint(name = "uk_service_tenant_title", columnNames = {"tenant_id", "title"}))
public class ServiceCatalogEntity extends TenantScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(length = 1000)
  private String description;

  @Column(name = "labor_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal laborPrice;

  @Column(name = "estimated_days")
  private Integer estimatedDays;

  @Column(nullable = false)
  private boolean active = true;

  protected ServiceCatalogEntity() {}

  public static ServiceCatalogEntity newService(String title, String description,
      BigDecimal laborPrice, Integer estimatedDays) {
    var s = new ServiceCatalogEntity();
    s.update(title, description, laborPrice, estimatedDays);
    s.active = true;
    return s;
  }

  public void update(String title, String description, BigDecimal laborPrice,
      Integer estimatedDays) {
    this.title = title;
    this.description = description;
    this.laborPrice = laborPrice;
    this.estimatedDays = estimatedDays;
  }

  public void deactivate() { this.active = false; }

  public UUID getId() { return id; }
  public String getTitle() { return title; }
  public String getDescription() { return description; }
  public BigDecimal getLaborPrice() { return laborPrice; }
  public Integer getEstimatedDays() { return estimatedDays; }
  public boolean isActive() { return active; }
}
