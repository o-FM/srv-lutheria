package com.luthier.service.domain.entities;

import com.luthier.service.domain.enums.PaymentMethod;
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
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Pagamento recebido de uma OS (imutável: estorno seria um novo lançamento). */
@Entity
@Immutable
@Table(name = "PAYMENT_TB")
public class PaymentEntity extends TenantScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, updatable = false)
  private ServiceOrderEntity order;

  @Column(nullable = false, precision = 12, scale = 2, updatable = false)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private PaymentMethod method;

  @Column(name = "receipt_number", nullable = false, length = 30, updatable = false)
  private String receiptNumber;

  @Column(name = "received_by", nullable = false, length = 254, updatable = false)
  private String receivedBy;

  protected PaymentEntity() {}

  static PaymentEntity of(ServiceOrderEntity order, BigDecimal amount, PaymentMethod method,
      String receiptNumber, String receivedBy) {
    var p = new PaymentEntity();
    p.order = order;
    p.amount = amount;
    p.method = method;
    p.receiptNumber = receiptNumber;
    p.receivedBy = receivedBy;
    return p;
  }

  public UUID getId() { return id; }
  public BigDecimal getAmount() { return amount; }
  public PaymentMethod getMethod() { return method; }
  public String getReceiptNumber() { return receiptNumber; }
  public String getReceivedBy() { return receivedBy; }
}
