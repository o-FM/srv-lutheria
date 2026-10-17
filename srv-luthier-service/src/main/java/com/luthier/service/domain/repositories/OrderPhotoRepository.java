package com.luthier.service.domain.repositories;

import com.luthier.service.domain.entities.OrderPhotoEntity;
import com.luthier.service.domain.records.PhotoResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Consultas filtradas pelo tenant atual (@TenantId). */
@Repository
public interface OrderPhotoRepository extends JpaRepository<OrderPhotoEntity, UUID> {

  /** Só metadados: o binário é lido apenas no download. */
  @Query("""
      select new com.luthier.service.domain.records.PhotoResponse(
        p.id, p.tag, p.caption, p.createdAt, p.user)
      from OrderPhotoEntity p where p.orderId = :orderId order by p.createdAt desc
      """)
  List<PhotoResponse> listByOrder(@Param("orderId") UUID orderId);

  long countByOrderId(UUID orderId);

  Optional<OrderPhotoEntity> findByIdAndOrderId(UUID id, UUID orderId);
}
