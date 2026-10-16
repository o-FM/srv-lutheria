package com.luthier.shared.audit;

import com.luthier.shared.tenancy.TenantContext;
import java.util.UUID;

/**
 * Ação sensível a ser registrada na trilha de auditoria (LGPD: quem fez o quê, quando, sobre
 * qual dado). Qualquer módulo publica; o módulo security grava. "details" nunca deve conter
 * dado pessoal em claro (use máscaras).
 */
public record AuditEvent(UUID tenantId, String action, String resourceType, String resourceId,
                         String details) {

  /** Na luthieria do contexto atual (requisição autenticada). */
  public static AuditEvent of(String action, String resourceType, Object resourceId,
      String details) {
    return new AuditEvent(TenantContext.current().orElse(null), action, resourceType,
        resourceId == null ? null : resourceId.toString(), details);
  }
}
