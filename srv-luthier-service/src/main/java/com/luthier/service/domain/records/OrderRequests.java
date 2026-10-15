package com.luthier.service.domain.records;

import com.luthier.service.domain.enums.OrderStatus;
import com.luthier.service.domain.enums.PaymentMethod;
import com.luthier.service.domain.enums.Priority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Requests da API de ordens de serviço. */
public final class OrderRequests {

  private OrderRequests() {}

  /** Wizard "Nova OS" (passo 3 confirma): cliente + instrumento + defeito + serviços + prazo. */
  public record CreateOrder(
      @NotNull UUID customerId,
      @NotNull UUID instrumentId,
      Priority priority,
      @NotBlank @Size(max = 2000) String reportedIssue,
      LocalDate estimatedDeliveryDate,
      @Valid @Size(max = 30) List<AddService> services) {}

  /** Com catalogId, título e preço vêm do catálogo (laborPrice pode sobrescrever o preço). */
  public record AddService(
      UUID catalogId,
      @Size(max = 120) String title,
      @Size(max = 1000) String description,
      @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal laborPrice) {}

  public record AddPart(@NotNull UUID productId, @NotNull @Positive Integer quantity) {}

  public record UpdateOrder(
      Priority priority,
      @Size(max = 2000) String diagnosisNotes,
      @Size(max = 2000) String technicianNotes,
      LocalDate estimatedDeliveryDate,
      @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal discount,
      @Size(max = 255) String paymentTerms) {}

  public record ChangeStatus(@NotNull OrderStatus status, @Size(max = 1000) String note) {}

  public record RejectQuote(@Size(max = 1000) String reason) {}

  public record AddNote(@NotBlank @Size(max = 1000) String note) {}

  public record ReceivePayment(
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
      @NotNull PaymentMethod method) {}
}
