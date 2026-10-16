package com.luthier.security.domain.repositories;

import com.luthier.security.domain.entities.CustomerAccessCodeEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerAccessCodeRepository extends JpaRepository<CustomerAccessCodeEntity, UUID> {

  Optional<CustomerAccessCodeEntity> findByCodeHash(String codeHash);

  boolean existsByCodeHash(String codeHash);

  /** Só o código mais recente vale. */
  @Modifying(flushAutomatically = true)
  @Query("""
      update CustomerAccessCodeEntity c set c.revokedAt = :now
      where c.tenantId = :tenantId and c.customerId = :customerId and c.revokedAt is null
      """)
  int revokeAll(@Param("tenantId") UUID tenantId, @Param("customerId") UUID customerId,
      @Param("now") Instant now);

  @Modifying
  @Query("delete from CustomerAccessCodeEntity c where c.expiresAt < :cutoff")
  int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
