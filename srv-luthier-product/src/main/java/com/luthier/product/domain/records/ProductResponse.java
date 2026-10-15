package com.luthier.product.domain.records;

import com.luthier.product.domain.entities.ProductEntity;
import com.luthier.product.domain.enums.ProductCategory;
import java.math.BigDecimal;
import java.util.UUID;

/** Espelha o StockItem do front. */
public record ProductResponse(UUID id, String name, ProductCategory category, String brand,
                              String sku, String barcode, int currentQuantity, int minQuantity,
                              boolean lowStock, String unit, BigDecimal costPrice,
                              BigDecimal salePrice, String location) {

  public static ProductResponse from(ProductEntity p) {
    return new ProductResponse(p.getId(), p.getName(), p.getCategory(), p.getBrand(), p.getSku(),
        p.getBarcode(), p.getCurrentQuantity(), p.getMinQuantity(), p.isLowStock(), p.getUnit(),
        p.getCostPrice(), p.getSalePrice(), p.getLocation());
  }
}
