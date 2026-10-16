package com.luthier.shared.validation;

/** Máscaras para logs e trilha de auditoria (LGPD: nunca dado pessoal em claro). */
public final class Masks {

  private Masks() {}

  /** "lucas.silveira@guitar.com" -> "lu***@guitar.com" */
  public static String email(String email) {
    if (email == null) return null;
    var at = email.indexOf('@');
    if (at < 0) return "***";
    return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
  }
}
