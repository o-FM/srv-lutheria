package com.luthier.shared.validation;

/** Telefones chegam mascarados do front ("(11) 98765-4321"); guardamos só os dígitos. */
public final class Phone {

  private Phone() {}

  public static String digits(String value) {
    if (value == null) return null;
    var d = value.replaceAll("\\D", "");
    return d.isEmpty() ? null : d;
  }
}
