package com.luthier.service.services;

import com.luthier.service.domain.records.CustomerDataExport;
import com.luthier.shared.authorization.Access;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * LGPD (art. 18): relatório com todos os dados que a luthieria guarda sobre um cliente.
 * Fica no módulo de serviços porque é o único que enxerga cliente E ordens de serviço.
 */
public interface PrivacyService {

  @PreAuthorize(Access.ADMIN)
  CustomerDataExport exportCustomer(UUID customerId);
}
