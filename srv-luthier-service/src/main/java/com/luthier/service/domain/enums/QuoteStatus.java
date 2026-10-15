package com.luthier.service.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.luthier.shared.errors.BadRequestException;
import java.util.Arrays;

/** Situação do orçamento. */
public enum QuoteStatus {
  PENDENTE("pendente"),
  APROVADO("aprovado"),
  REJEITADO("rejeitado");

  private final String value;

  QuoteStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static QuoteStatus fromValue(String value) {
    return Arrays.stream(values())
        .filter(e -> e.value.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new BadRequestException("Valor inválido: " + value));
  }
}
