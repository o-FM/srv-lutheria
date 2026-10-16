package com.luthier.security;

import java.time.Duration;
import java.util.UUID;

/**
 * API pública: código de acesso da área do cliente. O cliente entra digitando SÓ o código, então
 * ele é único na plataforma e tem 8 caracteres de um alfabeto sem ambiguidade (ex.: K7M4-P9QX).
 * Quem envia o código (WhatsApp) é o módulo de notificações; aqui só se gera e guarda o hash.
 */
public interface CustomerAccessCodes {

  /** Gera um código novo e revoga os anteriores do cliente. Retorna o valor formatado (XXXX-XXXX). */
  String issue(UUID tenantId, UUID customerId, String phoneDigits, Duration ttl);

  /** Revoga todos os códigos do cliente (ex.: dados anonimizados). */
  void revokeAll(UUID tenantId, UUID customerId);
}
