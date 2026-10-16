package com.luthier.security.controllers;

import com.luthier.security.domain.records.AuditLogResponse;
import com.luthier.security.services.AuditService;
import com.luthier.shared.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit-logs")
public class AuditController {

  private final AuditService auditService;

  public AuditController(AuditService auditService) {
    this.auditService = auditService;
  }

  @GetMapping
  public PageResponse<AuditLogResponse> list(@RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return auditService.list(PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200)));
  }
}
