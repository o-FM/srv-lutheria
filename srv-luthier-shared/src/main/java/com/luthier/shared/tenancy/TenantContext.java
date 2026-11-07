package com.luthier.shared.tenancy;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Tenant (luthieria) da requisição atual.
 *
 * Preenchido pelo filtro do módulo security a partir do claim "tid" do JWT e limpo ao final da
 * requisição. Há três estados (ver TenantIdentifierResolver):
 * <ul>
 *   <li><b>tenant</b>: o Hibernate filtra tudo por essa luthieria;</li>
 *   <li><b>sem tenant</b> (padrão: rotas /auth/**, bootstrap, listener ou job que esqueceu o
 *       {@link #runAs}): modo restrito, nenhum dado de negócio é lido nem gravado (SH-01);</li>
 *   <li><b>plataforma</b> ({@link #callAsPlatform}): leitura sem filtro, de todas as luthierias.
 *       Só existe quando pedida explicitamente.</li>
 * </ul>
 */
public final class TenantContext {

  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> PLATFORM = new ThreadLocal<>();

  private TenantContext() {}

  public static Optional<UUID> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  /** Tenant obrigatório: usado pelos casos de uso de negócio. */
  public static UUID require() {
    return current().orElseThrow(() -> new IllegalStateException("Tenant não definido no contexto"));
  }

  /** Verdadeiro só dentro de {@link #callAsPlatform}, e sem um tenant aninhado por cima. */
  public static boolean isPlatform() {
    return Boolean.TRUE.equals(PLATFORM.get());
  }

  /**
   * ROOT e NO_TENANT são estados do resolver, não luthierias. Aceitá-los como tenant daria a visão
   * sem filtro a quem conseguisse colocá-los no contexto, por exemplo um JWT com tid = ROOT.
   */
  public static boolean isReserved(UUID tenantId) {
    return TenantIdentifierResolver.ROOT.equals(tenantId)
        || TenantIdentifierResolver.NO_TENANT.equals(tenantId);
  }

  public static void set(UUID tenantId) {
    CURRENT.set(requireNotReserved(tenantId));
    PLATFORM.remove();
  }

  public static void clear() {
    CURRENT.remove();
    PLATFORM.remove();
  }

  /**
   * Executa um trecho como o tenant informado e restaura o estado anterior (útil em listeners e
   * jobs). Com {@code tenantId} nulo, o trecho roda no modo restrito, nunca como plataforma.
   *
   * Precisa envolver a transação, não rodar dentro dela: a sessão do Hibernate fixa o tenant na
   * abertura.
   */
  public static <T> T callAs(UUID tenantId, Supplier<T> action) {
    return within(tenantId, false, action);
  }

  /**
   * Executa SEM filtro de tenant (visão de plataforma, todas as luthierias). Uso restrito a
   * consultas de roteamento como "de qual luthieria é este token?" e a jobs de manutenção, como a
   * retenção. Gravar dados de negócio aqui falha (ver TenantScopedEntity).
   *
   * Assim como {@link #callAs}, precisa envolver a transação.
   */
  public static <T> T callAsPlatform(Supplier<T> action) {
    return within(null, true, action);
  }

  public static void runAs(UUID tenantId, Runnable action) {
    callAs(tenantId, () -> {
      action.run();
      return null;
    });
  }

  private static UUID requireNotReserved(UUID tenantId) {
    if (isReserved(tenantId)) {
      throw new IllegalArgumentException("Identificador reservado não é um tenant: " + tenantId);
    }
    return tenantId;
  }

  private static <T> T within(UUID tenantId, boolean platform, Supplier<T> action) {
    requireNotReserved(tenantId);
    var previousTenant = CURRENT.get();
    var previousPlatform = PLATFORM.get();
    apply(CURRENT, tenantId);
    apply(PLATFORM, platform ? Boolean.TRUE : null);
    try {
      return action.get();
    } finally {
      apply(CURRENT, previousTenant);
      apply(PLATFORM, previousPlatform);
    }
  }

  private static <V> void apply(ThreadLocal<V> holder, V value) {
    if (value == null) {
      holder.remove();
    } else {
      holder.set(value);
    }
  }
}
