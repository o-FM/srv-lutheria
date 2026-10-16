package com.luthier.security.services;

import com.luthier.security.domain.records.AuditLogResponse;
import com.luthier.shared.authorization.Access;
import com.luthier.shared.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

public interface AuditService {

  /** Trilha da luthieria do admin logado. */
  @PreAuthorize(Access.ADMIN)
  PageResponse<AuditLogResponse> list(Pageable pageable);
}
