package com.luthier.product.domain.entities;

import com.luthier.product.domain.enums.ProductCategory;
import com.luthier.shared.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Item de estoque (peça, corda, madeira, insumo). O saldo só muda via movimentação
 * (ver StockMovementEntity); o @Version herdado evita que duas baixas simultâneas se percam.
 */
@Entity
@Table(name = "PRODUCT_TB",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_tenant_sku", columnNames = {"tenant_id", "sku"}),
        @UniqueConstraint(name = "uk_product_tenant_barcode", columnNames = {"tenant_id", "barcode"})
    },
    indexes = @Index(name = "idx_product_tenant_name", columnList = "tenant_id, name"))
public class ProductEntity extends TenantScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 120)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProductCategory category;

  @Column(length = 60)
  private String brand;

  @Column(nullable = false, length = 40)
  private String sku;

  @Column(length = 40)
  private String barcode;

  /** un, jogo, m, ml, g */
  @Column(nullable = false, length = 10)
  private String unit;

  @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal costPrice;

  @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal salePrice;

  @Column(name = "current_quantity", nullable = false)
  private int currentQuantity;

  @Column(name = "min_quantity", nullable = false)
  private int minQuantity;

  @Column(length = 60)
  private String location;

  @Column(nullable = false)
  private boolean active = true;

  protected ProductEntity() {}

  public static ProductEntity newProduct(ProductData data) {
    var p = new ProductEntity();
    p.update(data);
    p.currentQuantity = 0;
    p.active = true;
    return p;
  }

  /** Dados cadastrais. NÃO mexe no saldo. */
  public void update(ProductData data) {
    this.name = data.name();
    this.category = data.category();
    this.brand = data.brand();
    this.sku = data.sku();
    this.barcode = data.barcode();
    this.unit = data.unit();
    this.costPrice = data.costPrice();
    this.salePrice = data.salePrice();
    this.minQuantity = data.minQuantity();
    this.location = data.location();
  }

  /** Aplica um delta ao saldo. Saldo negativo é proibido. */
  public void applyDelta(int delta) {
    if (currentQuantity + delta < 0) {
      throw new IllegalStateException("Saldo insuficiente");
    }
    currentQuantity += delta;
  }

  public boolean isLowStock() { return currentQuantity <= minQuantity; }

  public void deactivate() { this.active = false; }

  public record ProductData(String name, ProductCategory category, String brand, String sku,
                            String barcode, String unit, BigDecimal costPrice,
                            BigDecimal salePrice, int minQuantity, String location) {}

  public UUID getId() { return id; }
  public String getName() { return name; }
  public ProductCategory getCategory() { return category; }
  public String getBrand() { return brand; }
  public String getSku() { return sku; }
  public String getBarcode() { return barcode; }
  public String getUnit() { return unit; }
  public BigDecimal getCostPrice() { return costPrice; }
  public BigDecimal getSalePrice() { return salePrice; }
  public int getCurrentQuantity() { return currentQuantity; }
  public int getMinQuantity() { return minQuantity; }
  public String getLocation() { return location; }
  public boolean isActive() { return active; }
}
