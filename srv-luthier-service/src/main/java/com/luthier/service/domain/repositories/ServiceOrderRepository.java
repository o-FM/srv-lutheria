package com.luthier.service.domain.repositories;

import com.luthier.service.domain.entities.ServiceOrderEntity;
import com.luthier.service.domain.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Consultas já filtradas pelo tenant atual (@TenantId), exceto as marcadas como "plataforma". */
@Repository
public interface ServiceOrderRepository extends JpaRepository<ServiceOrderEntity, UUID> {

  /**
   * Portal do cliente: chamado SEM tenant no contexto (rota pública), então o Hibernate não
   * filtra e o token (aleatório, único global) localiza a OS em qualquer luthieria.
   */
  @Query("select o.tenantId from ServiceOrderEntity o where o.trackingToken = :token")
  Optional<UUID> findTenantByTrackingToken(@Param("token") String token);

  Optional<ServiceOrderEntity> findByTrackingToken(String token);

  @Query("""
      select o from ServiceOrderEntity o
      where (:status is null or o.status = :status)
        and (:customerId is null or o.customerId = :customerId)
        and (:number is null or o.orderNumber = :number)
      """)
  Page<ServiceOrderEntity> search(@Param("status") OrderStatus status,
      @Param("customerId") UUID customerId, @Param("number") Integer number, Pageable pageable);

  List<ServiceOrderEntity> findAllByCustomerIdOrderByOrderNumberDesc(UUID customerId);

  Optional<ServiceOrderEntity> findByIdAndCustomerId(UUID id, UUID customerId);

  // ---------------- relatórios ----------------

  @Query("select o.status, count(o) from ServiceOrderEntity o group by o.status")
  List<Object[]> countByStatus();

  @Query("""
      select count(o) from ServiceOrderEntity o
      where o.status in :open and o.estimatedDeliveryDate < :today
      """)
  long countOverdue(@Param("open") Collection<OrderStatus> open, @Param("today") LocalDate today);

  @Query("""
      select count(o) from ServiceOrderEntity o
      where o.status in :open and o.estimatedDeliveryDate = :today
      """)
  long countDueOn(@Param("open") Collection<OrderStatus> open, @Param("today") LocalDate today);

  @Query("""
      select count(o) from ServiceOrderEntity o
      where o.status = com.luthier.service.domain.enums.OrderStatus.ENTREGUE
        and o.deliveredAt >= :from and o.deliveredAt < :to
      """)
  long countDelivered(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

  /** A receber: OS prontas/entregues com saldo em aberto (total calculado no Java). */
  @Query("""
      select o from ServiceOrderEntity o
      where o.status in (com.luthier.service.domain.enums.OrderStatus.PRONTO,
                         com.luthier.service.domain.enums.OrderStatus.ENTREGUE,
                         com.luthier.service.domain.enums.OrderStatus.EM_EXECUCAO)
      """)
  List<ServiceOrderEntity> findBillable();

  @Query("select coalesce(sum(o.paidAmount), 0) from ServiceOrderEntity o")
  BigDecimal sumPaid();
}
