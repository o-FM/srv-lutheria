package com.luthier.shared.validation;

/** Regras de CPF compartilhadas (mesmo algoritmo de src/utils/cpfValidator.ts do front). */
public final class Cpf {

  private Cpf() {}

  /** Remove máscara: "123.456.789-09" -> "12345678909". Null/blank vira null. */
  public static String digits(String value) {
    if (value == null) return null;
    var d = value.replaceAll("\\D", "");
    return d.isEmpty() ? null : d;
  }

  public static boolean isValid(String value) {
    var cpf = digits(value);
    if (cpf == null || cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
      return false;
    }
    return checkDigit(cpf, 9) == cpf.charAt(9) - '0'
        && checkDigit(cpf, 10) == cpf.charAt(10) - '0';
  }

  /** LGPD: nunca devolver o CPF completo. "12345678909" -> "***.456.789-**". */
  public static String mask(String digits) {
    if (digits == null || digits.length() != 11) return null;
    return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
  }

  private static int checkDigit(String cpf, int length) {
    int sum = 0;
    for (int i = 0; i < length; i++) {
      sum += (cpf.charAt(i) - '0') * (length + 1 - i);
    }
    int rest = (sum * 10) % 11;
    return rest == 10 ? 0 : rest;
  }
}
