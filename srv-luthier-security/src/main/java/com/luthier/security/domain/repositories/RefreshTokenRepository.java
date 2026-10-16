package com.luthier.security.domain.repositories;

import com.luthier.security.domain.entities.RefreshTokenEntity;
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

/** Os métodos @Modifying exigem transação aberta pelo chamador (os services são @Transactional). */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

  Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

  /**
   * SELECT ... FOR UPDATE. Dois /auth/refresh simultâneos com o MESMO token não conseguem ambos
   * passar pela checagem "ainda não revogado": o segundo espera o primeiro e então vê revogado.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshTokenEntity t where t.tokenHash = :hash")
  Optional<RefreshTokenEntity> findForUpdateByTokenHash(@Param("hash") String hash);

  @Modifying(flushAutomatically = true)
  @Query("update RefreshTokenEntity t set t.revokedAt = :now "
      + "where t.familyId = :familyId and t.revokedAt is null")
  int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

  @Modifying(flushAutomatically = true)
  @Query("update RefreshTokenEntity t set t.revokedAt = :now "
      + "where t.userId = :userId and t.revokedAt is null")
  int revokeAllByUser(@Param("userId") UUID userId, @Param("now") Instant now);

  @Modifying
  @Query("delete from RefreshTokenEntity t where t.expiresAt < :cutoff")
  int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
