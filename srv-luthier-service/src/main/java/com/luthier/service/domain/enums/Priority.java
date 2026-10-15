package com.luthier.service.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.luthier.shared.errors.BadRequestException;
import java.util.Arrays;

/** Prioridade da OS (igual ao front). */
public enum Priority {
  BAIXA("baixa"),
  NORMAL("normal"),
  ALTA("alta"),
  URGENTE("urgente");

  private final String value;

  Priority(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static Priority fromValue(String value) {
    return Arrays.stream(values())
        .filter(e -> e.value.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new BadRequestException("Valor inválido: " + value));
  }
}
