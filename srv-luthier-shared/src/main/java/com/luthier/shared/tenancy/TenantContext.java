package com.luthier.shared.tenancy;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Tenant (luthieria) da requisição atual.
 *
 * Preenchido pelo filtro do módulo security a partir do claim "tid" do JWT e limpo ao final da
 * requisição. Código sem tenant (rotas /auth/**, bootstrap, jobs) roda como "plataforma": o
 * Hibernate não aplica o filtro de tenant (ver TenantIdentifierResolver).
 */
public final class TenantContext {

  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  private TenantContext() {}

  public static Optional<UUID> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  /** Tenant obrigatório: usado pelos casos de uso de negócio. */
  public static UUID require() {
    return current().orElseThrow(() -> new IllegalStateException("Tenant não definido no contexto"));
  }

  public static void set(UUID tenantId) {
    CURRENT.set(tenantId);
  }

  public static void clear() {
    CURRENT.remove();
  }

  /** Executa um trecho como o tenant informado e restaura o anterior (útil em listeners e jobs). */
  public static <T> T callAs(UUID tenantId, Supplier<T> action) {
    var previous = CURRENT.get();
    CURRENT.set(tenantId);
    try {
      return action.get();
    } finally {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
  }

  public static void runAs(UUID tenantId, Runnable action) {
    callAs(tenantId, () -> {
      action.run();
      return null;
    });
  }
}
