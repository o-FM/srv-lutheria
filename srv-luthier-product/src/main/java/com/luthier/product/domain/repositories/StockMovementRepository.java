package com.luthier.product.domain.repositories;

import com.luthier.product.domain.entities.StockMovementEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovementEntity, UUID> {

  Page<StockMovementEntity> findAllByProductIdOrderByCreatedAtDesc(UUID productId,
      Pageable pageable);
}
