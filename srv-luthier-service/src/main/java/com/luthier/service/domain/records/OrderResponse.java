package com.luthier.service.domain.records;

import com.luthier.service.domain.entities.OrderPartEntity;
import com.luthier.service.domain.entities.OrderServiceItemEntity;
import com.luthier.service.domain.entities.OrderTimelineEntity;
import com.luthier.service.domain.entities.PaymentEntity;
import com.luthier.service.domain.entities.ServiceOrderEntity;
import com.luthier.service.domain.enums.OrderStatus;
import com.luthier.service.domain.enums.PaymentMethod;
import com.luthier.service.domain.enums.Priority;
import com.luthier.service.domain.enums.QuoteStatus;
import com.luthier.service.domain.enums.TimelineType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Espelha o tipo Order do front (com quote, services, parts, timeline e payments). */
public record OrderResponse(UUID id, int orderNumber, UUID clientId, UUID instrumentId,
                            OrderStatus status, Priority priority, String reportedIssue,
                            String diagnosisNotes, String technicianNotes, Instant entryDate,
                            LocalDate estimatedDeliveryDate, Instant completedDate,
                            Instant deliveredDate, List<ServiceItem> services,
                            List<Part> parts, Quote quote, List<Timeline> timeline,
                            List<Payment> payments, BigDecimal totalAmount,
                            BigDecimal paidAmount, BigDecimal balance,
                            String clientTrackingToken) {

  public record ServiceItem(UUID id, UUID catalogId, String title, String description,
                            BigDecimal laborPrice, boolean done) {}

  public record Part(UUID id, UUID stockItemId, String stockItemName, int quantity,
                     BigDecimal unitPrice, BigDecimal total) {}

  public record Quote(BigDecimal laborTotal, BigDecimal partsTotal, BigDecimal discount,
                      BigDecimal grandTotal, QuoteStatus status, Instant sentAt,
                      Instant answeredAt, String rejectionReason, String paymentTerms) {}

  public record Timeline(UUID id, Instant timestamp, TimelineType type, String title,
                         String description, String user) {}

  public record Payment(UUID id, BigDecimal amount, PaymentMethod method, Instant date,
                        String receiptNumber, String receivedBy) {}

  public static OrderResponse from(ServiceOrderEntity o) {
    return new OrderResponse(o.getId(), o.getOrderNumber(), o.getCustomerId(),
        o.getInstrumentId(), o.getStatus(), o.getPriority(), o.getReportedIssue(),
        o.getDiagnosisNotes(), o.getTechnicianNotes(), o.getCreatedAt(),
        o.getEstimatedDeliveryDate(), o.getCompletedAt(), o.getDeliveredAt(),
        o.getServices().stream().map(OrderResponse::service).toList(),
        o.getParts().stream().map(OrderResponse::part).toList(),
        quote(o),
        timeline(o.getTimeline()),
        o.getPayments().stream().map(OrderResponse::payment).toList(),
        o.grandTotal(), o.getPaidAmount(), o.balance(), o.getTrackingToken());
  }

  /** Versão enxuta para listagens. */
  public static OrderResponse summary(ServiceOrderEntity o) {
    return new OrderResponse(o.getId(), o.getOrderNumber(), o.getCustomerId(),
        o.getInstrumentId(), o.getStatus(), o.getPriority(), o.getReportedIssue(), null, null,
        o.getCreatedAt(), o.getEstimatedDeliveryDate(), o.getCompletedAt(), o.getDeliveredAt(),
        List.of(), List.of(), null, List.of(), List.of(), o.grandTotal(), o.getPaidAmount(),
        o.balance(), null);
  }

  static Quote quote(ServiceOrderEntity o) {
    return new Quote(o.laborTotal(), o.partsTotal(), o.getDiscount(), o.grandTotal(),
        o.getQuoteStatus(), o.getQuoteSentAt(), o.getQuoteAnsweredAt(), o.getRejectionReason(),
        o.getPaymentTerms());
  }

  static ServiceItem service(OrderServiceItemEntity s) {
    return new ServiceItem(s.getId(), s.getCatalogId(), s.getTitle(), s.getDescription(),
        s.getLaborPrice(), s.isDone());
  }

  static Part part(OrderPartEntity p) {
    return new Part(p.getId(), p.getProductId(), p.getProductName(), p.getQuantity(),
        p.getUnitPrice(), p.total());
  }

  static List<Timeline> timeline(List<OrderTimelineEntity> events) {
    return events.stream()
        .sorted(Comparator.comparing(OrderTimelineEntity::getCreatedAt,
            Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
        .map(t -> new Timeline(t.getId(), t.getCreatedAt(), t.getType(), t.getTitle(),
            t.getDescription(), t.getUser()))
        .toList();
  }

  static Payment payment(PaymentEntity p) {
    return new Payment(p.getId(), p.getAmount(), p.getMethod(), p.getCreatedAt(),
        p.getReceiptNumber(), p.getReceivedBy());
  }
}
