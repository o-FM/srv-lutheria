package com.luthier.service.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.luthier.shared.errors.BadRequestException;
import java.util.Arrays;

/** Formas de pagamento (igual ao front). */
public enum PaymentMethod {
  PIX("pix"),
  CARTAO_CREDITO("cartao_credito"),
  CARTAO_DEBITO("cartao_debito"),
  DINHEIRO("dinheiro"),
  TRANSFERENCIA("transferencia");

  private final String value;

  PaymentMethod(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static PaymentMethod fromValue(String value) {
    return Arrays.stream(values())
        .filter(e -> e.value.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new BadRequestException("Valor inválido: " + value));
  }
}
