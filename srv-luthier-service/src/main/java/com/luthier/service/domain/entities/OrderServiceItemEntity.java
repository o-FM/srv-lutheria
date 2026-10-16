package com.luthier.service.domain.entities;

import com.luthier.shared.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Serviço (mão de obra) dentro de uma OS. Título e preço copiados do catálogo no momento. */
@Entity
@Table(name = "SERVICE_ORDER_ITEM_TB")
public class OrderServiceItemEntity extends TenantScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, updatable = false)
  private ServiceOrderEntity order;

  @Column(name = "catalog_id")
  private UUID catalogId;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(length = 1000)
  private String description;

  @Column(name = "labor_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal laborPrice;

  @Column(nullable = false)
  private boolean done;

  protected OrderServiceItemEntity() {}

  static OrderServiceItemEntity of(ServiceOrderEntity order, UUID catalogId, String title,
      String description, BigDecimal laborPrice) {
    var i = new OrderServiceItemEntity();
    i.order = order;
    i.catalogId = catalogId;
    i.title = title;
    i.description = description;
    i.laborPrice = laborPrice;
    i.done = false;
    return i;
  }

  void markDone() { this.done = true; }

  void update(String title, String description, BigDecimal laborPrice) {
    if (title != null) this.title = title;
    if (description != null) this.description = description.isBlank() ? null : description.strip();
    this.laborPrice = laborPrice;
  }

  public UUID getId() { return id; }
  public UUID getCatalogId() { return catalogId; }
  public String getTitle() { return title; }
  public String getDescription() { return description; }
  public BigDecimal getLaborPrice() { return laborPrice; }
  public boolean isDone() { return done; }
}
