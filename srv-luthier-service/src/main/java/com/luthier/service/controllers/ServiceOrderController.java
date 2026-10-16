package com.luthier.service.controllers;

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
import com.luthier.service.services.ServiceOrderService;
import com.luthier.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/service-orders")
@Validated
public class ServiceOrderController {

  private static final int MAX_PAGE_SIZE = 100;

  private final ServiceOrderService service;

  public ServiceOrderController(ServiceOrderService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OrderResponse create(@Valid @RequestBody CreateOrder request, Principal principal) {
    return service.create(request, principal.getName());
  }

  /** Lista/Kanban: ?status=em_execucao&customerId=...&number=1042 */
  @GetMapping
  public PageResponse<OrderResponse> search(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) Integer number,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
        Sort.by(Sort.Direction.DESC, "orderNumber"));
    var parsed = status == null || status.isBlank() ? null : OrderStatus.fromValue(status);
    return service.search(parsed, customerId, number, pageable);
  }

  @GetMapping("/{id}")
  public OrderResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PatchMapping("/{id}")
  public OrderResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateOrder request) {
    return service.update(id, request);
  }

  @PostMapping("/{id}/services")
  @ResponseStatus(HttpStatus.CREATED)
  public OrderResponse addService(@PathVariable UUID id, @Valid @RequestBody AddService request) {
    return service.addService(id, request);
  }

  @DeleteMapping("/{id}/services/{itemId}")
  public OrderResponse removeService(@PathVariable UUID id, @PathVariable UUID itemId) {
    return service.removeService(id, itemId);
  }

  @PostMapping("/{id}/services/{itemId}/done")
  public OrderResponse markServiceDone(@PathVariable UUID id, @PathVariable UUID itemId,
      Principal principal) {
    return service.markServiceDone(id, itemId, principal.getName());
  }

  @PostMapping("/{id}/parts")
  @ResponseStatus(HttpStatus.CREATED)
  public OrderResponse addPart(@PathVariable UUID id, @Valid @RequestBody AddPart request) {
    return service.addPart(id, request);
  }

  @DeleteMapping("/{id}/parts/{partId}")
  public OrderResponse removePart(@PathVariable UUID id, @PathVariable UUID partId) {
    return service.removePart(id, partId);
  }

  @PostMapping("/{id}/status")
  public OrderResponse changeStatus(@PathVariable UUID id,
      @Valid @RequestBody ChangeStatus request, Principal principal) {
    return service.changeStatus(id, request, principal.getName());
  }

  @PostMapping("/{id}/quote/send")
  public OrderResponse sendQuote(@PathVariable UUID id, Principal principal) {
    return service.sendQuote(id, principal.getName());
  }

  @PostMapping("/{id}/quote/approve")
  public OrderResponse approveQuote(@PathVariable UUID id, Principal principal) {
    return service.approveQuote(id, principal.getName());
  }

  @PostMapping("/{id}/quote/reject")
  public OrderResponse rejectQuote(@PathVariable UUID id,
      @Valid @RequestBody RejectQuote request, Principal principal) {
    return service.rejectQuote(id, request, principal.getName());
  }

  @PostMapping("/{id}/notes")
  public OrderResponse addNote(@PathVariable UUID id, @Valid @RequestBody AddNote request,
      Principal principal) {
    return service.addNote(id, request, principal.getName());
  }

  @PostMapping("/{id}/payments")
  @ResponseStatus(HttpStatus.CREATED)
  public OrderResponse receivePayment(@PathVariable UUID id,
      @Valid @RequestBody ReceivePayment request, Principal principal) {
    return service.receivePayment(id, request, principal.getName());
  }
}
