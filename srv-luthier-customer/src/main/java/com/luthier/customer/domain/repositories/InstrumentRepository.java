package com.luthier.customer.domain.repositories;

import com.luthier.customer.domain.entities.InstrumentEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstrumentRepository extends JpaRepository<InstrumentEntity, UUID> {

  List<InstrumentEntity> findAllByCustomerIdOrderByCreatedAtDesc(UUID customerId);

  List<InstrumentEntity> findAllByCustomerIdInOrderByCreatedAtDesc(Collection<UUID> customerIds);

  Optional<InstrumentEntity> findByIdAndCustomerId(UUID id, UUID customerId);

  org.springframework.data.domain.Page<InstrumentEntity> findAllByOrderByCreatedAtDesc(
      org.springframework.data.domain.Pageable pageable);
}
