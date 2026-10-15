package com.luthier.customer.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.luthier.errors.BadRequestException;
import java.util.Arrays;

/** Valores na API iguais ao front (src/types/index.ts: InstrumentType). */
public enum InstrumentType {
  VIOLAO_ACO("violao_aco"),
  VIOLAO_NYLON("violao_nylon"),
  GUITARRA("guitarra"),
  BAIXO("baixo"),
  CAVAQUINHO("cavaquinho"),
  VIOLA("viola"),
  VIOLINO("violino"),
  OUTRO("outro");

  private final String value;

  InstrumentType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static InstrumentType fromValue(String value) {
    return Arrays.stream(values())
        .filter(t -> t.value.equalsIgnoreCase(value) || t.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new BadRequestException("Tipo de instrumento inválido: " + value));
  }
}
