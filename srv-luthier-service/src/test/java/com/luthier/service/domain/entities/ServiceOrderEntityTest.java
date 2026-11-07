package com.luthier.service.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luthier.service.domain.enums.OrderStatus;
import com.luthier.service.domain.enums.PaymentMethod;
import com.luthier.service.domain.enums.Priority;
import com.luthier.shared.errors.ConflictException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * SRV-01 (docs/auditoria.md): total e saldo da OS nunca ficam negativos. Regras decididas em
 * docs/correcoes.prd.md:
 * <ul>
 *   <li>o desconto nunca passa do subtotal, nem depois de remover itens;</li>
 *   <li>não se removem itens de OS com pagamento registrado;</li>
 *   <li>só se recebe pagamento depois do orçamento aprovado (em_execucao, pronto, entregue).</li>
 * </ul>
 */
class ServiceOrderEntityTest {

  private static final String USER = "luthier@teste.com";

  @Test
  void removingAPartBelowTheDiscountIsRejected() {
    var order = order();
    service(order, "100");
    var part = part(order, "50");
    discount(order, "120");

    assertThatThrownBy(() -> order.removePart(part.getId()))
        .isInstanceOf(ConflictException.class);
    assertThat(order.grandTotal()).isEqualByComparingTo("30");
    assertThat(order.getParts()).hasSize(1);
  }

  @Test
  void removingAServiceBelowTheDiscountIsRejected() {
    var order = order();
    var service = service(order, "100");
    part(order, "50");
    discount(order, "60");

    assertThatThrownBy(() -> order.removeService(service.getId()))
        .isInstanceOf(ConflictException.class);
    assertThat(order.grandTotal()).isEqualByComparingTo("90");
    assertThat(order.getServices()).hasSize(1);
  }

  @Test
  void removingAnItemThatKeepsTheDiscountValidWorks() {
    var order = order();
    service(order, "100");
    var part = part(order, "50");
    discount(order, "10");

    order.removePart(part.getId());

    assertThat(order.grandTotal()).isEqualByComparingTo("90");
  }

  @Test
  void discountEqualToTheSubtotalIsAllowed() {
    var order = order();
    var service = service(order, "100");
    part(order, "50");
    discount(order, "50");

    order.removeService(service.getId());

    assertThat(order.grandTotal()).isEqualByComparingTo("0");
  }

  @Test
  void paymentBeforeQuoteApprovalIsRejected() {
    var order = order();
    service(order, "100");
    assertPaymentRejected(order);                       // recebido

    order.changeStatus(OrderStatus.DIAGNOSTICO, null, USER);
    assertPaymentRejected(order);

    order.changeStatus(OrderStatus.ORCAMENTO, null, USER);
    assertPaymentRejected(order);

    order.sendQuote(USER);                              // aguardando_aprovacao
    assertPaymentRejected(order);
    assertThat(order.getPaidAmount()).isEqualByComparingTo("0");
  }

  @Test
  void paymentAfterApprovalIsAccepted() {
    var order = approvedOrder("150");

    order.receivePayment(new BigDecimal("50"), PaymentMethod.PIX, USER);

    assertThat(order.balance()).isEqualByComparingTo("100");
  }

  @Test
  void paymentOnDeliveredOrderIsAccepted() {
    var order = approvedOrder("150");
    order.changeStatus(OrderStatus.PRONTO, null, USER);
    order.changeStatus(OrderStatus.ENTREGUE, null, USER);

    order.receivePayment(new BigDecimal("150"), PaymentMethod.DINHEIRO, USER);

    assertThat(order.balance()).isEqualByComparingTo("0");
  }

  @Test
  void cancelledOrderStillRejectsPayments() {
    var order = order();
    service(order, "100");
    order.changeStatus(OrderStatus.CANCELADO, null, USER);

    assertPaymentRejected(order);
  }

  /**
   * Defesa redundante: pela máquina de estados atual não há como ter pagamento numa OS editável.
   * O teste força o estado para garantir que a regra continua valendo se as transições mudarem.
   */
  @Test
  void itemsCannotBeRemovedOnceAPaymentExists() {
    var order = order();
    var service = service(order, "100");
    var part = part(order, "50");
    ReflectionTestUtils.setField(order, "paidAmount", new BigDecimal("20"));

    assertThatThrownBy(() -> order.removeService(service.getId()))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> order.removePart(part.getId()))
        .isInstanceOf(ConflictException.class);
    assertThat(order.getServices()).hasSize(1);
    assertThat(order.getParts()).hasSize(1);
  }

  /** Dado gravado antes da regra (ou por outro caminho) não pode virar orçamento negativo. */
  @Test
  void quoteWithDiscountAboveSubtotalCannotBeSent() {
    var order = order();
    service(order, "100");
    ReflectionTestUtils.setField(order, "discount", new BigDecimal("999"));
    order.changeStatus(OrderStatus.ORCAMENTO, null, USER);

    assertThatThrownBy(() -> order.sendQuote(USER)).isInstanceOf(ConflictException.class);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.ORCAMENTO);
  }

  // ---------------- helpers ----------------

  private static ServiceOrderEntity order() {
    return ServiceOrderEntity.open(1, UUID.randomUUID(), UUID.randomUUID(), Priority.NORMAL,
        "Trastes gastos", null, "token", USER);
  }

  private static ServiceOrderEntity approvedOrder(String labor) {
    var order = order();
    service(order, labor);
    order.changeStatus(OrderStatus.ORCAMENTO, null, USER);
    order.sendQuote(USER);
    order.approveQuote(USER);
    return order;
  }

  /** O id só existe depois de persistir; aqui é atribuído à mão para a remoção funcionar. */
  private static OrderServiceItemEntity service(ServiceOrderEntity order, String labor) {
    var item = order.addService(null, "Regulagem", null, new BigDecimal(labor));
    ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
    return item;
  }

  private static OrderPartEntity part(ServiceOrderEntity order, String unitPrice) {
    var part = order.addPart(UUID.randomUUID(), "Encordoamento", 1, new BigDecimal(unitPrice));
    ReflectionTestUtils.setField(part, "id", UUID.randomUUID());
    return part;
  }

  private static void discount(ServiceOrderEntity order, String value) {
    order.updateDetails(null, null, null, null, new BigDecimal(value), null);
  }

  private static void assertPaymentRejected(ServiceOrderEntity order) {
    assertThatThrownBy(() -> order.receivePayment(BigDecimal.ONE, PaymentMethod.PIX, USER))
        .as("pagamento com a OS em %s", order.getStatus())
        .isInstanceOf(ConflictException.class);
  }
}
