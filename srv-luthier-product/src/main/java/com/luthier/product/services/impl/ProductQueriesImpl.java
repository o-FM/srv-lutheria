package com.luthier.product.services.impl;

import com.luthier.product.ProductQueries;
import com.luthier.product.domain.repositories.ProductRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductQueriesImpl implements ProductQueries {

  private final ProductRepository productRepository;

  public ProductQueriesImpl(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  @Override
  public Optional<ProductSnapshot> find(UUID productId) {
    return productRepository.findByIdAndActiveTrue(productId)
        .map(p -> new ProductSnapshot(p.getId(), p.getName(), p.getSku(), p.getUnit(),
            p.getSalePrice(), p.getCurrentQuantity()));
  }

  @Override
  public long countLowStock() {
    return productRepository.countLowStock();
  }
}
