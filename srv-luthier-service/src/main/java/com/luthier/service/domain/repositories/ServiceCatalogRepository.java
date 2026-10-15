package com.luthier.service.domain.repositories;

import com.luthier.service.domain.entities.ServiceCatalogEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Consultas já filtradas pelo tenant atual (@TenantId). */
@Repository
public interface ServiceCatalogRepository extends JpaRepository<ServiceCatalogEntity, UUID> {

  List<ServiceCatalogEntity> findAllByActiveTrueOrderByTitleAsc();

  Optional<ServiceCatalogEntity> findByIdAndActiveTrue(UUID id);

  boolean existsByTitleIgnoreCase(String title);

  boolean existsByTitleIgnoreCaseAndIdNot(String title, UUID id);
}
