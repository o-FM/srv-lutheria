package com.luthier.service.controllers;

import com.luthier.service.domain.records.OrderRequests.RejectQuote;
import com.luthier.service.domain.records.PortalOrderResponse;
import com.luthier.service.services.ClientAreaService;
import com.luthier.service.services.ClientAreaService.ClientProfile;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Área do cliente: exige token de cliente (POST /auth/customer/login). */
@RestController
@RequestMapping("/client")
public class ClientAreaController {

  private final ClientAreaService service;

  public ClientAreaController(ClientAreaService service) {
    this.service = service;
  }

  @GetMapping("/me")
  public ClientProfile me() {
    return service.me();
  }

  @GetMapping("/orders")
  public List<PortalOrderResponse> orders() {
    return service.orders();
  }

  @GetMapping("/orders/{id}")
  public PortalOrderResponse order(@PathVariable UUID id) {
    return service.order(id);
  }

  @PostMapping("/orders/{id}/quote/approve")
  public PortalOrderResponse approve(@PathVariable UUID id) {
    return service.approveQuote(id);
  }

  @PostMapping("/orders/{id}/quote/reject")
  public PortalOrderResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectQuote request) {
    return service.rejectQuote(id, request.reason());
  }
}
