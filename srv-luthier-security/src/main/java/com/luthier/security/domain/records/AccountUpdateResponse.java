package com.luthier.security.domain.records;

/**
 * Trocar e-mail ou senha encerra todas as sessões; {@code session} traz os tokens novos do
 * aparelho que fez a alteração (null quando só o telefone mudou).
 */
public record AccountUpdateResponse(UserResponse user, TokenResponse session) {
}
