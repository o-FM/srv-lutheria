package com.luthier.shared.tenancy;

import java.util.Optional;
import java.util.UUID;

/**
 * Cliente logado na área do cliente (claim "cid" do JWT). Preenchido pelo mesmo filtro que define
 * o tenant. Casos de uso do cliente SEMPRE filtram por este id — nunca por um id vindo da URL.
 */
public final class CustomerContext {

  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  private CustomerContext() {}

  public static Optional<UUID> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  public static UUID require() {
    return current().orElseThrow(() -> new IllegalStateException("Cliente não definido no contexto"));
  }

  public static void set(UUID customerId) {
    CURRENT.set(customerId);
  }

  public static void clear() {
    CURRENT.remove();
  }
}
