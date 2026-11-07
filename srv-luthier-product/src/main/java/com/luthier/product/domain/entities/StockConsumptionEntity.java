package com.luthier.product.domain.entities;

import com.luthier.shared.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Marcador de baixa de estoque já aplicada para uma OS (PRO-01). A unicidade por
 * (tenant_id, order_id) garante uma baixa por OS, mesmo com o outbox reentregando o evento.
 */
@Entity
@Immutable
@Table(name = "STOCK_CONSUMPTION_TB", uniqueConstraints =
    @UniqueConstraint(name = "uk_stock_consumption_order", columnNames = {"tenant_id", "order_id"}))
public class StockConsumptionEntity extends TenantScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false, updatable = false)
  private UUID orderId;

  @Column(nullable = false, length = 255, updatable = false)
  private String reference;

  protected StockConsumptionEntity() {}

  public static StockConsumptionEntity of(UUID orderId, String reference) {
    var c = new StockConsumptionEntity();
    c.orderId = orderId;
    c.reference = reference;
    return c;
  }

  public UUID getId() { return id; }
  public UUID getOrderId() { return orderId; }
  public String getReference() { return reference; }
}
