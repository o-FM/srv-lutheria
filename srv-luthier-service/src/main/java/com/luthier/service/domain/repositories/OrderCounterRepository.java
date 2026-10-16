package com.luthier.service.domain.repositories;

import com.luthier.service.domain.entities.OrderCounterEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderCounterRepository extends JpaRepository<OrderCounterEntity, UUID> {

  /** Um contador por luthieria (filtro de tenant); FOR UPDATE serializa aberturas simultâneas. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from OrderCounterEntity c")
  Optional<OrderCounterEntity> lockCurrent();
}
