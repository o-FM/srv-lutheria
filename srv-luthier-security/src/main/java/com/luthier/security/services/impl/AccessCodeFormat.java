package com.luthier.security.services.impl;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Código amigável: 8 caracteres de um alfabeto de 30 símbolos sem os que se confundem
 * (0/O, 1/I/L, U), exibido como XXXX-XXXX. 30⁸ ≈ 6,6 × 10¹¹ combinações: com o rate limit por
 * IP, adivinhar um código válido é inviável. Digitação tolerante: minúsculas, espaços e traço.
 */
final class AccessCodeFormat {

  static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
  static final int LENGTH = 8;
  private static final SecureRandom RANDOM = new SecureRandom();

  private AccessCodeFormat() {}

  /** Retorna o código canônico (sem traço), para guardar o hash. */
  static String generate() {
    var sb = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) {
      sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }

  static String display(String canonical) {
    return canonical.substring(0, 4) + "-" + canonical.substring(4);
  }

  /** Normaliza o que o cliente digitou; null se não tiver o formato de um código. */
  static String normalize(String typed) {
    var c = typed.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    if (c.length() != LENGTH) return null;
    for (char ch : c.toCharArray()) {
      if (ALPHABET.indexOf(ch) < 0) return null;
    }
    return c;
  }
}
