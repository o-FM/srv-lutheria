package com.luthier.security.domain.repositories;

import com.luthier.security.domain.entities.CustomerAccessCodeEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerAccessCodeRepository extends JpaRepository<CustomerAccessCodeEntity, UUID> {

  /** Códigos ainda válidos para o telefone na luthieria (FOR UPDATE: tentativas concorrentes). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select c from CustomerAccessCodeEntity c
      where c.tenantId = :tenantId and c.phone = :phone and c.usedAt is null and c.expiresAt > :now
      """)
  List<CustomerAccessCodeEntity> findOpenForUpdate(@Param("tenantId") UUID tenantId,
      @Param("phone") String phone, @Param("now") Instant now);

  /** Só o código mais recente vale. */
  @Modifying(flushAutomatically = true)
  @Query("""
      update CustomerAccessCodeEntity c set c.usedAt = :now
      where c.tenantId = :tenantId and c.customerId = :customerId and c.usedAt is null
      """)
  int invalidatePending(@Param("tenantId") UUID tenantId, @Param("customerId") UUID customerId,
      @Param("now") Instant now);

  @Modifying
  @Query("delete from CustomerAccessCodeEntity c where c.expiresAt < :cutoff")
  int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
