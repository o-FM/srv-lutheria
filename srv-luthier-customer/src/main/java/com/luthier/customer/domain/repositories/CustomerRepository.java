package com.luthier.customer.domain.repositories;

import com.luthier.customer.domain.entities.CustomerEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Todas as consultas já saem filtradas pelo tenant atual (@TenantId na entidade). */
@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {

  Optional<CustomerEntity> findByIdAndActiveTrue(UUID id);

  boolean existsByIdAndActiveTrue(UUID id);

  boolean existsByCpf(String cpf);

  boolean existsByCpfAndIdNot(String cpf, UUID id);

  Page<CustomerEntity> findAllByActiveTrue(Pageable pageable);

  /** Busca do front: nome, telefone ou CPF (os dois últimos por dígitos). */
  @Query("""
      select c from CustomerEntity c
      where c.active = true
        and (lower(c.name) like lower(concat('%', :text, '%'))
             or (:digits <> '' and (c.phone like concat('%', :digits, '%')
                                    or c.cpf like concat('%', :digits, '%'))))
      """)
  Page<CustomerEntity> search(@Param("text") String text, @Param("digits") String digits,
      Pageable pageable);
}
