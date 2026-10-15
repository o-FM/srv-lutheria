package com.luthier.service.domain.entities;

import com.luthier.service.domain.enums.TimelineType;
import com.luthier.shared.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Linha do tempo da OS (histórico imutável, também exibido no portal do cliente). */
@Entity
@Immutable
@Table(name = "SERVICE_ORDER_TIMELINE_TB")
public class OrderTimelineEntity extends TenantScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, updatable = false)
  private ServiceOrderEntity order;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private TimelineType type;

  @Column(nullable = false, length = 160, updatable = false)
  private String title;

  @Column(length = 1000, updatable = false)
  private String description;

  @Column(name = "actor", nullable = false, length = 254, updatable = false)
  private String user;

  protected OrderTimelineEntity() {}

  static OrderTimelineEntity of(ServiceOrderEntity order, TimelineType type, String title,
      String description, String user) {
    var t = new OrderTimelineEntity();
    t.order = order;
    t.type = type;
    t.title = title;
    t.description = description;
    t.user = user;
    return t;
  }

  public UUID getId() { return id; }
  public TimelineType getType() { return type; }
  public String getTitle() { return title; }
  public String getDescription() { return description; }
  public String getUser() { return user; }
}
