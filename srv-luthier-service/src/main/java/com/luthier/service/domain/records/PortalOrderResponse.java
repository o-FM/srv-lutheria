package com.luthier.service.domain.records;

import com.luthier.service.domain.entities.ServiceOrderEntity;
import com.luthier.service.domain.enums.OrderStatus;
import java.time.LocalDate;
import java.util.List;

/**
 * Visão do portal do cliente (acesso sem senha, por token). Mínimo de dados pessoais (LGPD):
 * só o primeiro nome, sem CPF/telefone/e-mail, sem notas internas da bancada nem pagamentos.
 */
public record PortalOrderResponse(int orderNumber, String customerFirstName, String instrument,
                                  OrderStatus status, String statusLabel,
                                  LocalDate estimatedDeliveryDate,
                                  List<OrderResponse.ServiceItem> services,
                                  List<OrderResponse.Part> parts, OrderResponse.Quote quote,
                                  List<OrderResponse.Timeline> timeline) {

  public static PortalOrderResponse from(ServiceOrderEntity o, String customerName,
      String instrument) {
    var firstName = customerName == null ? null : customerName.strip().split("\\s+")[0];
    return new PortalOrderResponse(o.getOrderNumber(), firstName, instrument, o.getStatus(),
        o.getStatus().getLabel(), o.getEstimatedDeliveryDate(),
        o.getServices().stream().map(OrderResponse::service).toList(),
        o.getParts().stream().map(OrderResponse::part).toList(),
        OrderResponse.quote(o),
        OrderResponse.timeline(o.getTimeline()).stream()
            .map(t -> new OrderResponse.Timeline(t.id(), t.timestamp(), t.type(), t.title(),
                null, null))
            .toList());
  }
}
