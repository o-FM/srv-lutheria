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

/**
 * Peça usada na OS. Referencia o produto por ID (outro módulo) e congela nome e preço do momento
 * em que foi orçada. A baixa do estoque acontece quando a OS fica pronta (evento).
 */
@Entity
@Table(name = "SERVICE_ORDER_PART_TB")
public class OrderPartEntity extends TenantScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, updatable = false)
  private ServiceOrderEntity order;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "product_name", nullable = false, length = 120)
  private String productName;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  protected OrderPartEntity() {}

  static OrderPartEntity of(ServiceOrderEntity order, UUID productId, String productName,
      int quantity, BigDecimal unitPrice) {
    var p = new OrderPartEntity();
    p.order = order;
    p.productId = productId;
    p.productName = productName;
    p.quantity = quantity;
    p.unitPrice = unitPrice;
    return p;
  }

  public BigDecimal total() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }

  public UUID getId() { return id; }
  public UUID getProductId() { return productId; }
  public String getProductName() { return productName; }
  public int getQuantity() { return quantity; }
  public BigDecimal getUnitPrice() { return unitPrice; }
}
