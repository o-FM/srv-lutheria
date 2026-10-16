package com.luthier.service.services;

import com.luthier.service.domain.enums.OrderStatus;
import com.luthier.service.domain.records.OrderRequests.AddNote;
import com.luthier.service.domain.records.OrderRequests.AddPart;
import com.luthier.service.domain.records.OrderRequests.AddService;
import com.luthier.service.domain.records.OrderRequests.ChangeStatus;
import com.luthier.service.domain.records.OrderRequests.CreateOrder;
import com.luthier.service.domain.records.OrderRequests.ReceivePayment;
import com.luthier.service.domain.records.OrderRequests.RejectQuote;
import com.luthier.service.domain.records.OrderRequests.UpdateOrder;
import com.luthier.service.domain.records.OrderResponse;
import com.luthier.shared.authorization.Access;
import com.luthier.shared.web.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Ordens de serviço da luthieria do usuário logado. "user" é quem executa a ação (vai para a
 * linha do tempo). Balcão (atendente) abre OS, envia orçamento e recebe; bancada (luthier) mexe
 * em peças, executa e conclui.
 */
public interface ServiceOrderService {

  @PreAuthorize(Access.CUSTOMER_DESK)
  OrderResponse create(CreateOrder request, String user);

  @PreAuthorize(Access.CUSTOMER_DESK)
  PageResponse<OrderResponse> search(OrderStatus status, UUID customerId, Integer number,
      Pageable pageable);

  @PreAuthorize(Access.CUSTOMER_DESK)
  OrderResponse get(UUID id);

  @PreAuthorize(Access.CUSTOMER_DESK)
  OrderResponse update(UUID id, UpdateOrder request);

  @PreAuthorize(Access.CUSTOMER_DESK)
  OrderResponse addService(UUID id, AddService request);

  @PreAuthorize(Access.CUSTOMER_DESK)
  OrderResponse removeService(UUID id, UUID itemId);

  @PreAuthorize(Access.ADMIN_OR_LUTHIER)
  OrderResponse markServiceDone(UUID id, UUID itemId, String user);

  @PreAuthorize(Access.ADMIN_OR_LUTHIER)
  OrderResponse addPart(UUID id, AddPart request);

  @PreAuthorize(Access.ADMIN_OR_LUTHIER)
  OrderResponse removePart(UUID id, UUID partId);

  /** "pronto" exige LUTHIER/ADMIN (conferido no serviço); demais transições, balcão. */
  @PreAuthorize(Access.CUSTOMER_DESK)
  OrderResponse changeStatus(UUID id, ChangeStatus request, String user);

  @PreAuthorize(Access.CUSTOMER_DESK)
  OrderResponse sendQuote(UUID id, String user);

  /** Aprovação/recusa registrada pela oficina (ex.: cliente respondeu por telefone). */
  @PreAuthorize(Access.CUSTOMER_DESK)
  OrderResponse approveQuote(UUID id, String user);

  @PreAuthorize(Access.CUSTOMER_DESK)
  OrderResponse rejectQuote(UUID id, RejectQuote request, String user);

  @PreAuthorize(Access.CUSTOMER_DESK)
  OrderResponse addNote(UUID id, AddNote request, String user);

  @PreAuthorize(Access.CUSTOMER_DESK)
  OrderResponse receivePayment(UUID id, ReceivePayment request, String user);
}
