package com.luthier.product.domain.repositories;

import com.luthier.product.domain.entities.ProductEntity;
import com.luthier.product.domain.enums.ProductCategory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Consultas já filtradas pelo tenant atual (@TenantId). */
@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

  Optional<ProductEntity> findByIdAndActiveTrue(UUID id);

  Optional<ProductEntity> findByBarcodeAndActiveTrue(String barcode);

  boolean existsBySku(String sku);

  boolean existsBySkuAndIdNot(String sku, UUID id);

  boolean existsByBarcode(String barcode);

  boolean existsByBarcodeAndIdNot(String barcode, UUID id);

  @Query("select count(p) from ProductEntity p where p.active = true "
      + "and p.currentQuantity <= p.minQuantity")
  long countLowStock();

  @Query("""
      select p from ProductEntity p
      where p.active = true
        and (:category is null or p.category = :category)
        and (:lowStock = false or p.currentQuantity <= p.minQuantity)
        and (:text = '' or lower(p.name) like lower(concat('%', :text, '%'))
             or lower(p.brand) like lower(concat('%', :text, '%'))
             or lower(p.sku) like lower(concat('%', :text, '%'))
             or p.barcode = :text)
      """)
  Page<ProductEntity> search(@Param("text") String text,
      @Param("category") ProductCategory category, @Param("lowStock") boolean lowStock,
      Pageable pageable);
}
