package com.luthier.security;

import java.time.Duration;
import java.util.UUID;

/**
 * API pública: emite códigos de acesso de uso único para a área do cliente. Quem envia o código
 * (WhatsApp) é o módulo de notificações; aqui só se gera e guarda o hash.
 */
public interface CustomerAccessCodes {

  /** Código de 6 dígitos. Invalida os códigos anteriores do mesmo cliente. Retorna o valor bruto. */
  String issue(UUID tenantId, UUID customerId, String phoneDigits, Duration ttl);
}
