package com.luthier.customer.domain.repositories;

import com.luthier.customer.domain.entities.InstrumentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstrumentRepository extends JpaRepository<InstrumentEntity, UUID> {

  List<InstrumentEntity> findAllByCustomerIdOrderByCreatedAtDesc(UUID customerId);

  Optional<InstrumentEntity> findByIdAndCustomerId(UUID id, UUID customerId);
}
