package com.luthier.security.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado quando alguém pede recuperação de senha para um e-mail existente e ativo.
 * Quem envia o e-mail (módulo notification) escuta este evento. O módulo security NÃO sabe
 * enviar e-mail: só avisa que aconteceu.
 *
 * ATENÇÃO: "token" é o valor BRUTO (necessário para montar o link). Nunca logar este record.
 * Sugestão de link: https://app.luthieria.com.br/redefinir-senha#token=XXXX (fragmento '#' não
 * vai para logs de servidor nem para o header Referer).
 */
public record PasswordResetRequested(UUID userId, String email, String token, Instant expiresAt) {

  @Override
  public String toString() {
    return "PasswordResetRequested[userId=" + userId + ", token=***]";
  }
}
