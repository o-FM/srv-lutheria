package com.luthier.tenancy;

import java.util.Map;
import java.util.UUID;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Multi-tenancy por coluna discriminadora (banco e schema compartilhados).
 *
 * Toda entidade com @TenantId (ver TenantScopedEntity) é filtrada automaticamente pelo Hibernate
 * em SELECT/UPDATE/DELETE e recebe o tenant no INSERT. Sem tenant no contexto, devolvemos ROOT,
 * que o Hibernate trata como "sem filtro" (isRoot) — reservado a código de plataforma.
 */
@Component
public class TenantIdentifierResolver
    implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

  public static final UUID ROOT = new UUID(0L, 0L);

  @Override
  public UUID resolveCurrentTenantIdentifier() {
    return TenantContext.current().orElse(ROOT);
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return false;
  }

  @Override
  public boolean isRoot(UUID tenantId) {
    return ROOT.equals(tenantId);
  }

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
  }
}
