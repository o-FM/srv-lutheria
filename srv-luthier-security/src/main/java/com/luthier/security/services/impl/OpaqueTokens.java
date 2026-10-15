package com.luthier.security.services.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Gera e faz hash de tokens opacos (refresh e reset).
 *
 * - 32 bytes de SecureRandom = 256 bits de entropia, então SHA-256 simples basta como hash
 *   (BCrypt/Argon2 são para senhas humanas, de baixa entropia; aqui só encareceriam cada request).
 * - O valor bruto só existe na resposta ao cliente / no e-mail. No banco fica apenas o hash.
 */
final class OpaqueTokens {

  private static final SecureRandom RANDOM = new SecureRandom();

  private OpaqueTokens() {}

  static String generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String hash(String rawToken) {
    try {
      var digest = MessageDigest.getInstance("SHA-256")
          .digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 indisponível", e);
    }
  }
}
