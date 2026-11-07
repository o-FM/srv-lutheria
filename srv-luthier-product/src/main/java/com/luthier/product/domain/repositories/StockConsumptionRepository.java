package com.luthier.product.domain.repositories;

import com.luthier.product.domain.entities.StockConsumptionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockConsumptionRepository extends JpaRepository<StockConsumptionEntity, UUID> {

  /** Filtrado pelo tenant do contexto, como toda entidade TenantScopedEntity. */
  boolean existsByOrderId(UUID orderId);
}
