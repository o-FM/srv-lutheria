package com.luthier.domain.repositories;

import com.luthier.domain.entities.PasswordResetTokenEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {

  /** Lock para garantir uso único mesmo com duas requisições simultâneas. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from PasswordResetTokenEntity t where t.tokenHash = :hash")
  Optional<PasswordResetTokenEntity> findForUpdateByTokenHash(@Param("hash") String hash);

  /** Ao pedir um novo link, os anteriores ainda pendentes deixam de valer. */
  @Modifying(flushAutomatically = true)
  @Query("update PasswordResetTokenEntity t set t.usedAt = :now "
      + "where t.userId = :userId and t.usedAt is null")
  int invalidatePending(@Param("userId") UUID userId, @Param("now") Instant now);

  @Modifying
  @Query("delete from PasswordResetTokenEntity t where t.expiresAt < :cutoff")
  int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}

