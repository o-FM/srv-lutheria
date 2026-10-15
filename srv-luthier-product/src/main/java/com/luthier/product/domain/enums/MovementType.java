package com.luthier.product.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.luthier.errors.BadRequestException;
import java.util.Arrays;

/** Igual ao StockMovement.type do front. AJUSTE define o saldo absoluto (inventário). */
public enum MovementType {
  ENTRADA("entrada"),
  SAIDA("saida"),
  AJUSTE("ajuste");

  private final String value;

  MovementType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static MovementType fromValue(String value) {
    return Arrays.stream(values())
        .filter(t -> t.value.equalsIgnoreCase(value) || t.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new BadRequestException("Tipo de movimento inválido: " + value));
  }
}
