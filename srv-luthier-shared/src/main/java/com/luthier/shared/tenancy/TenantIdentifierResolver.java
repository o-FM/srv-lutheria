package com.luthier.shared.tenancy;

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
 * em SELECT/UPDATE/DELETE e recebe o tenant no INSERT.
 *
 * Falha fechado (SH-01). Sem tenant no contexto, devolvemos {@link #NO_TENANT}, que não é root e
 * não existe em tenant_tb:
 * <ul>
 *   <li>as leituras de negócio voltam vazias;</li>
 *   <li>UPDATE/DELETE em massa não afetam nenhuma linha;</li>
 *   <li>INSERT é barrado pelo TenantScopedEntity e pela FK para tenant_tb (APP-03).</li>
 * </ul>
 * {@link #ROOT}, a visão sem filtro, só é devolvido dentro de TenantContext.callAsPlatform.
 *
 * Não dá para lançar exceção aqui: o Hibernate resolve o tenant ao abrir QUALQUER sessão. Isso
 * inclui o login, o bootstrap, a auditoria e o outbox do Modulith, que só tocam tabelas sem
 * tenant.
 */
@Component
public class TenantIdentifierResolver
    implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

  /** Visão de plataforma: o Hibernate não aplica o filtro de tenant. */
  public static final UUID ROOT = new UUID(0L, 0L);

  /** Sem tenant e fora do escopo de plataforma: filtra por um tenant que não existe. */
  public static final UUID NO_TENANT = new UUID(0L, 1L);

  @Override
  public UUID resolveCurrentTenantIdentifier() {
    return TenantContext.current()
        .orElseGet(() -> TenantContext.isPlatform() ? ROOT : NO_TENANT);
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
