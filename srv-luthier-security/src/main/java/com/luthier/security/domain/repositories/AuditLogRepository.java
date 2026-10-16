package com.luthier.security.domain.repositories;

import com.luthier.security.domain.entities.AuditLogEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

  Page<AuditLogEntity> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

  @Modifying
  @Query("delete from AuditLogEntity a where a.createdAt < :cutoff")
  int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
