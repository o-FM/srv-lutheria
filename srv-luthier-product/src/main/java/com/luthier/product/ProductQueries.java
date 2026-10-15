package com.luthier.product;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública do módulo de produtos para outros módulos (ex.: ordens de serviço).
 * Sempre no escopo da luthieria (tenant) atual.
 */
public interface ProductQueries {

  Optional<ProductSnapshot> find(UUID productId);

  long countLowStock();

  record ProductSnapshot(UUID id, String name, String sku, String unit, BigDecimal salePrice,
                         int currentQuantity) {}
}
