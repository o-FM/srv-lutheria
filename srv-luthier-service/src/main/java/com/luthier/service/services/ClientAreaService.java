package com.luthier.service.services;

import com.luthier.service.domain.records.PortalOrderResponse;
import com.luthier.shared.authorization.Access;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Área do cliente (login por código no WhatsApp). Tudo é filtrado pelo cliente do token
 * (CustomerContext) além do tenant: o cliente só enxerga as próprias OS.
 */
public interface ClientAreaService {

  @PreAuthorize(Access.CUSTOMER)
  ClientProfile me();

  @PreAuthorize(Access.CUSTOMER)
  List<PortalOrderResponse> orders();

  @PreAuthorize(Access.CUSTOMER)
  PortalOrderResponse order(UUID orderId);

  @PreAuthorize(Access.CUSTOMER)
  PortalOrderResponse approveQuote(UUID orderId);

  @PreAuthorize(Access.CUSTOMER)
  PortalOrderResponse rejectQuote(UUID orderId, String reason);

  record ClientProfile(UUID id, String name, String workshop) {}
}
