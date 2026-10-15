package com.luthier.product.domain.entities;

import com.luthier.product.domain.enums.MovementType;
import com.luthier.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Movimentação de estoque: registro imutável (trilha de auditoria do saldo). */
@Entity
@Immutable
@Table(name = "STOCK_MOVEMENT_TB", indexes =
    @Index(name = "idx_movement_tenant_product", columnList = "tenant_id, product_id"))
public class StockMovementEntity extends TenantScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10, updatable = false)
  private MovementType type;

  /** Quantidade informada (ENTRADA/SAIDA: positiva; AJUSTE: saldo final contado). */
  @Column(nullable = false, updatable = false)
  private int quantity;

  /** Variação efetiva no saldo (+/-). */
  @Column(nullable = false, updatable = false)
  private int delta;

  @Column(name = "balance_after", nullable = false, updatable = false)
  private int balanceAfter;

  @Column(nullable = false, length = 255, updatable = false)
  private String reason;

  /** OS que consumiu a peça, quando houver (referência por ID ao módulo de serviços). */
  @Column(name = "order_id", updatable = false)
  private UUID orderId;

  @Column(name = "created_by", length = 254, updatable = false)
  private String createdBy;

  protected StockMovementEntity() {}

  public static StockMovementEntity record(UUID productId, MovementType type, int quantity,
      int delta, int balanceAfter, String reason, UUID orderId, String createdBy) {
    var m = new StockMovementEntity();
    m.productId = productId;
    m.type = type;
    m.quantity = quantity;
    m.delta = delta;
    m.balanceAfter = balanceAfter;
    m.reason = reason;
    m.orderId = orderId;
    m.createdBy = createdBy;
    return m;
  }

  public UUID getId() { return id; }
  public UUID getProductId() { return productId; }
  public MovementType getType() { return type; }
  public int getQuantity() { return quantity; }
  public int getDelta() { return delta; }
  public int getBalanceAfter() { return balanceAfter; }
  public String getReason() { return reason; }
  public UUID getOrderId() { return orderId; }
  public String getCreatedBy() { return createdBy; }
}
