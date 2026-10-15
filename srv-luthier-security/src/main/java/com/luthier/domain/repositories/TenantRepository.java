package com.luthier.domain.repositories;

import com.luthier.domain.entities.TenantEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

  boolean existsBySlug(String slug);

  @Query("select count(t) > 0 from TenantEntity t where t.id = :id and t.active = true")
  boolean isActive(@Param("id") UUID id);
}
