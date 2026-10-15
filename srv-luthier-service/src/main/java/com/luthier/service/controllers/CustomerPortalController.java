package com.luthier.service.controllers;

import com.luthier.service.domain.records.OrderRequests.RejectQuote;
import com.luthier.service.domain.records.PortalOrderResponse;
import com.luthier.service.services.CustomerPortalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Portal do cliente, sem senha: link único com o token da OS. Rota pública e com rate limit. */
@RestController
@RequestMapping("/portal/orders/{token}")
@Validated
public class CustomerPortalController {

  private static final String TOKEN = "^[A-Za-z0-9_-]{32}$";

  private final CustomerPortalService portal;

  public CustomerPortalController(CustomerPortalService portal) {
    this.portal = portal;
  }

  @GetMapping
  public PortalOrderResponse view(@PathVariable @Pattern(regexp = TOKEN) String token) {
    return portal.view(token);
  }

  @PostMapping("/quote/approve")
  public PortalOrderResponse approve(@PathVariable @Pattern(regexp = TOKEN) String token) {
    return portal.approveQuote(token);
  }

  @PostMapping("/quote/reject")
  public PortalOrderResponse reject(@PathVariable @Pattern(regexp = TOKEN) String token,
      @Valid @RequestBody RejectQuote request) {
    return portal.rejectQuote(token, request.reason());
  }
}
