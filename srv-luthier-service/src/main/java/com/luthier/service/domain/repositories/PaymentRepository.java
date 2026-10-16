package com.luthier.service.domain.repositories;

import com.luthier.service.domain.entities.PaymentEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

  /** [método, soma, quantidade] no período. */
  @Query("""
      select p.method, sum(p.amount), count(p) from PaymentEntity p
      where p.createdAt >= :from and p.createdAt < :to
      group by p.method
      """)
  List<Object[]> sumByMethod(@Param("from") Instant from, @Param("to") Instant to);
}
