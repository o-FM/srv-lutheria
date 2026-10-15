package com.luthier.service.services;

import com.luthier.service.domain.records.PortalOrderResponse;

/**
 * Portal do cliente: acesso público pelo token da OS (sem login). Não tem @PreAuthorize de
 * propósito; a segurança é o token aleatório de 192 bits + rate limit na rota.
 */
public interface CustomerPortalService {

  PortalOrderResponse view(String token);

  PortalOrderResponse approveQuote(String token);

  PortalOrderResponse rejectQuote(String token, String reason);
}
