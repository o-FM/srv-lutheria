package com.luthier.service.services;

import com.luthier.service.domain.records.ReportResponses.Dashboard;
import com.luthier.service.domain.records.ReportResponses.Financial;
import com.luthier.shared.authorization.Access;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;

/** Relatórios da luthieria do usuário logado. */
public interface ReportService {

  @PreAuthorize(Access.ANY_STAFF)
  Dashboard dashboard();

  /** Financeiro é restrito ao dono/luthier (canAccessFinancials do front). */
  @PreAuthorize(Access.ADMIN_OR_LUTHIER)
  Financial financial(LocalDate from, LocalDate to);
}
