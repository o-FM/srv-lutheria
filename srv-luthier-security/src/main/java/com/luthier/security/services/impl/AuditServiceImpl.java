package com.luthier.security.services.impl;

import com.luthier.security.domain.entities.AuditLogEntity;
import com.luthier.security.domain.records.AuditLogResponse;
import com.luthier.security.domain.repositories.AuditLogRepository;
import com.luthier.security.services.AuditService;
import com.luthier.shared.audit.AuditEvent;
import com.luthier.shared.tenancy.TenantContext;
import com.luthier.shared.web.PageResponse;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditServiceImpl implements AuditService {

  private final AuditLogRepository repository;

  public AuditServiceImpl(AuditLogRepository repository) {
    this.repository = repository;
  }

  /**
   * Grava em transação própria (REQUIRES_NEW): tentativas que falham e desfazem a transação de
   * negócio (ex.: login inválido) também ficam registradas.
   */
  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditEvent event) {
    repository.save(AuditLogEntity.of(event.tenantId(), currentActor(), event.action(),
        event.resourceType(), event.resourceId(), event.details(), currentIp()));
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<AuditLogResponse> list(Pageable pageable) {
    return PageResponse.of(
        repository.findAllByTenantIdOrderByCreatedAtDesc(TenantContext.require(), pageable),
        AuditLogResponse::from);
  }

  private static String currentActor() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth == null || auth.getName() == null ? "anônimo" : auth.getName();
  }

  private static String currentIp() {
    return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
        ? attrs.getRequest().getRemoteAddr() : null;
  }
}
