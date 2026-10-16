package com.luthier.service.domain.entities;

import com.luthier.shared.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Numeração sequencial de OS por luthieria (OS #1, #2...). Lida com lock pessimista. */
@Entity
@Table(name = "SERVICE_ORDER_COUNTER_TB")
public class OrderCounterEntity extends TenantScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "last_number", nullable = false)
  private int lastNumber;

  protected OrderCounterEntity() {}

  public static OrderCounterEntity start() {
    var c = new OrderCounterEntity();
    c.lastNumber = 0;
    return c;
  }

  public int next() {
    return ++lastNumber;
  }
}
