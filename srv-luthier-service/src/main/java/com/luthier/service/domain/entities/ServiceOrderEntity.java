package com.luthier.service.domain.entities;

import com.luthier.service.domain.enums.OrderStatus;
import com.luthier.service.domain.enums.PaymentMethod;
import com.luthier.service.domain.enums.Priority;
import com.luthier.service.domain.enums.QuoteStatus;
import com.luthier.service.domain.enums.TimelineType;
import com.luthier.shared.errors.BadRequestException;
import com.luthier.shared.errors.ConflictException;
import com.luthier.shared.errors.NotFoundException;
import com.luthier.shared.tenancy.TenantScopedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Agregado Ordem de Serviço. Toda regra de negócio da OS passa por aqui: transições de status
 * (OrderStatus), edição de itens só antes do orçamento, totais e pagamentos. Cliente e
 * instrumento (módulo customer) e produtos (módulo product) são referenciados apenas por ID.
 */
@Entity
@Table(name = "SERVICE_ORDER_TB",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_tenant_number", columnNames = {"tenant_id", "order_number"}),
        @UniqueConstraint(name = "uk_order_tracking_token", columnNames = "tracking_token")
    },
    indexes = {
        @Index(name = "idx_order_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_order_tenant_customer", columnList = "tenant_id, customer_id")
    })
public class ServiceOrderEntity extends TenantScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_number", nullable = false, updatable = false)
  private int orderNumber;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(name = "instrument_id", nullable = false, updatable = false)
  private UUID instrumentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 25)
  private OrderStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Priority priority;

  @Column(name = "reported_issue", nullable = false, length = 2000)
  private String reportedIssue;

  @Column(name = "diagnosis_notes", length = 2000)
  private String diagnosisNotes;

  @Column(name = "technician_notes", length = 2000)
  private String technicianNotes;

  @Column(name = "estimated_delivery_date")
  private LocalDate estimatedDeliveryDate;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  /** Token aleatório (não sequencial) do portal do cliente — acesso sem senha. */
  @Column(name = "tracking_token", nullable = false, length = 43, updatable = false)
  private String trackingToken;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal discount = BigDecimal.ZERO;

  @Column(name = "payment_terms", length = 255)
  private String paymentTerms;

  @Enumerated(EnumType.STRING)
  @Column(name = "quote_status", length = 10)
  private QuoteStatus quoteStatus;

  @Column(name = "quote_sent_at")
  private Instant quoteSentAt;

  @Column(name = "quote_answered_at")
  private Instant quoteAnsweredAt;

  @Column(name = "rejection_reason", length = 1000)
  private String rejectionReason;

  @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal paidAmount = BigDecimal.ZERO;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("createdAt")
  private List<OrderServiceItemEntity> services = new ArrayList<>();

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("createdAt")
  private List<OrderPartEntity> parts = new ArrayList<>();

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
  @OrderBy("createdAt desc")
  private List<OrderTimelineEntity> timeline = new ArrayList<>();

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
  @OrderBy("createdAt")
  private List<PaymentEntity> payments = new ArrayList<>();

  protected ServiceOrderEntity() {}

  public static ServiceOrderEntity open(int orderNumber, UUID customerId, UUID instrumentId,
      Priority priority, String reportedIssue, LocalDate estimatedDeliveryDate,
      String trackingToken, String user) {
    var o = new ServiceOrderEntity();
    o.orderNumber = orderNumber;
    o.customerId = customerId;
    o.instrumentId = instrumentId;
    o.priority = priority == null ? Priority.NORMAL : priority;
    o.reportedIssue = reportedIssue;
    o.estimatedDeliveryDate = estimatedDeliveryDate;
    o.trackingToken = trackingToken;
    o.status = OrderStatus.RECEBIDO;
    o.log(TimelineType.STATUS_CHANGE, "Instrumento recebido", reportedIssue, user);
    return o;
  }

  // ---------------- itens ----------------

  public OrderServiceItemEntity addService(UUID catalogId, String title, String description,
      BigDecimal laborPrice) {
    requireEditable();
    var item = OrderServiceItemEntity.of(this, catalogId, title, description, laborPrice);
    services.add(item);
    return item;
  }

  public void removeService(UUID itemId) {
    requireEditable();
    if (!services.removeIf(s -> itemId.equals(s.getId()))) {
      throw new NotFoundException("Service item not found!");
    }
  }

  public void markServiceDone(UUID itemId, String user) {
    if (status != OrderStatus.EM_EXECUCAO) {
      throw new ConflictException("Serviços só podem ser concluídos com a OS em execução");
    }
    var item = services.stream().filter(s -> itemId.equals(s.getId())).findFirst()
        .orElseThrow(() -> new NotFoundException("Service item not found!"));
    if (!item.isDone()) {
      item.markDone();
      log(TimelineType.SERVICE_DONE, "Serviço concluído: " + item.getTitle(), null, user);
    }
  }

  public OrderPartEntity addPart(UUID productId, String productName, int quantity,
      BigDecimal unitPrice) {
    requireEditable();
    var part = OrderPartEntity.of(this, productId, productName, quantity, unitPrice);
    parts.add(part);
    return part;
  }

  public void removePart(UUID partId) {
    requireEditable();
    if (!parts.removeIf(p -> partId.equals(p.getId()))) {
      throw new NotFoundException("Part not found!");
    }
  }

  // ---------------- dados gerais ----------------

  public void updateDetails(Priority priority, String diagnosisNotes, String technicianNotes,
      LocalDate estimatedDeliveryDate, BigDecimal discount, String paymentTerms) {
    if (!status.isOpen()) {
      throw new ConflictException("OS encerrada não pode ser alterada");
    }
    if (priority != null) this.priority = priority;
    if (diagnosisNotes != null) this.diagnosisNotes = diagnosisNotes;
    if (technicianNotes != null) this.technicianNotes = technicianNotes;
    if (estimatedDeliveryDate != null) this.estimatedDeliveryDate = estimatedDeliveryDate;
    if (paymentTerms != null) this.paymentTerms = paymentTerms;
    if (discount != null) {
      requireEditable();
      if (discount.compareTo(laborTotal().add(partsTotal())) > 0) {
        throw new BadRequestException("Desconto maior que o total da OS");
      }
      this.discount = discount;
    }
  }

  public void recordPhoto(String tag, String caption, String user) {
    log(TimelineType.PHOTO_ADDED, "Foto adicionada: " + tag, caption, user);
  }

  public void addNote(String note, String user) {
    log(TimelineType.NOTE_ADDED, "Observação", note, user);
  }

  // ---------------- status / orçamento ----------------

  /** Transições simples. Envio/resposta de orçamento têm métodos próprios. */
  public OrderStatus changeStatus(OrderStatus target, String note, String user) {
    if (target == OrderStatus.AGUARDANDO_APROVACAO) {
      throw new BadRequestException("Use o envio de orçamento para aguardar aprovação");
    }
    if (status == OrderStatus.AGUARDANDO_APROVACAO && target == OrderStatus.EM_EXECUCAO) {
      throw new BadRequestException("A execução começa com a aprovação do orçamento");
    }
    var previous = transition(target);
    var now = Instant.now();
    if (target == OrderStatus.PRONTO) {
      completedAt = now;
      services.forEach(OrderServiceItemEntity::markDone);
    }
    if (target == OrderStatus.ENTREGUE) {
      deliveredAt = now;
    }
    log(TimelineType.STATUS_CHANGE, target.getLabel(), note, user);
    return previous;
  }

  public void sendQuote(String user) {
    if (services.isEmpty() && parts.isEmpty()) {
      throw new BadRequestException("Orçamento sem serviços ou peças");
    }
    transition(OrderStatus.AGUARDANDO_APROVACAO);
    quoteStatus = QuoteStatus.PENDENTE;
    quoteSentAt = Instant.now();
    quoteAnsweredAt = null;
    rejectionReason = null;
    log(TimelineType.QUOTE_SENT, "Orçamento enviado: R$ " + grandTotal(), paymentTerms, user);
  }

  public void approveQuote(String user) {
    requireQuotePending();
    transition(OrderStatus.EM_EXECUCAO);
    quoteStatus = QuoteStatus.APROVADO;
    quoteAnsweredAt = Instant.now();
    log(TimelineType.QUOTE_APPROVED, "Orçamento aprovado", null, user);
  }

  /** Rejeição volta para ORCAMENTO: a oficina pode revisar e reenviar, ou cancelar. */
  public void rejectQuote(String reason, String user) {
    requireQuotePending();
    transition(OrderStatus.ORCAMENTO);
    quoteStatus = QuoteStatus.REJEITADO;
    quoteAnsweredAt = Instant.now();
    rejectionReason = reason;
    log(TimelineType.QUOTE_REJECTED, "Orçamento recusado", reason, user);
  }

  // ---------------- pagamentos ----------------

  public PaymentEntity receivePayment(BigDecimal amount, PaymentMethod method, String user) {
    if (status == OrderStatus.CANCELADO) {
      throw new ConflictException("OS cancelada não recebe pagamentos");
    }
    if (amount.signum() <= 0) {
      throw new BadRequestException("Valor deve ser maior que zero");
    }
    if (amount.compareTo(balance()) > 0) {
      throw new ConflictException("Valor maior que o saldo devedor de R$ " + balance());
    }
    var receipt = "REC-%d-%02d".formatted(orderNumber, payments.size() + 1);
    var payment = PaymentEntity.of(this, amount, method, receipt, user);
    payments.add(payment);
    paidAmount = paidAmount.add(amount);
    log(TimelineType.PAYMENT_RECEIVED, "Pagamento recebido: R$ " + amount,
        method.getValue() + " · " + receipt, user);
    return payment;
  }

  // ---------------- totais ----------------

  public BigDecimal laborTotal() {
    return services.stream().map(OrderServiceItemEntity::getLaborPrice)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal partsTotal() {
    return parts.stream().map(OrderPartEntity::total).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal grandTotal() {
    return laborTotal().add(partsTotal()).subtract(discount);
  }

  public BigDecimal balance() {
    return grandTotal().subtract(paidAmount);
  }

  // ---------------- internos ----------------

  private OrderStatus transition(OrderStatus target) {
    if (!status.canGoTo(target)) {
      throw new ConflictException("Transição inválida: " + status.getValue() + " → "
          + target.getValue());
    }
    var previous = status;
    status = target;
    return previous;
  }

  private void requireEditable() {
    if (!status.isEditable()) {
      throw new ConflictException("Itens só podem ser alterados antes do envio do orçamento");
    }
  }

  private void requireQuotePending() {
    if (status != OrderStatus.AGUARDANDO_APROVACAO || quoteStatus != QuoteStatus.PENDENTE) {
      throw new ConflictException("Não há orçamento aguardando resposta");
    }
  }

  private void log(TimelineType type, String title, String description, String user) {
    timeline.add(OrderTimelineEntity.of(this, type, title, description, user));
  }

  // ---------------- getters ----------------

  public UUID getId() { return id; }
  public int getOrderNumber() { return orderNumber; }
  public UUID getCustomerId() { return customerId; }
  public UUID getInstrumentId() { return instrumentId; }
  public OrderStatus getStatus() { return status; }
  public Priority getPriority() { return priority; }
  public String getReportedIssue() { return reportedIssue; }
  public String getDiagnosisNotes() { return diagnosisNotes; }
  public String getTechnicianNotes() { return technicianNotes; }
  public LocalDate getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
  public Instant getCompletedAt() { return completedAt; }
  public Instant getDeliveredAt() { return deliveredAt; }
  public String getTrackingToken() { return trackingToken; }
  public BigDecimal getDiscount() { return discount; }
  public String getPaymentTerms() { return paymentTerms; }
  public QuoteStatus getQuoteStatus() { return quoteStatus; }
  public Instant getQuoteSentAt() { return quoteSentAt; }
  public Instant getQuoteAnsweredAt() { return quoteAnsweredAt; }
  public String getRejectionReason() { return rejectionReason; }
  public BigDecimal getPaidAmount() { return paidAmount; }
  public List<OrderServiceItemEntity> getServices() { return Collections.unmodifiableList(services); }
  public List<OrderPartEntity> getParts() { return Collections.unmodifiableList(parts); }
  public List<OrderTimelineEntity> getTimeline() { return Collections.unmodifiableList(timeline); }
  public List<PaymentEntity> getPayments() { return Collections.unmodifiableList(payments); }
}
