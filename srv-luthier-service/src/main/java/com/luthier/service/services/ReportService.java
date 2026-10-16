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

  /** Faturamento é restrito ao dono (ADMIN). */
  @PreAuthorize(Access.ADMIN)
  Financial financial(LocalDate from, LocalDate to);
}
