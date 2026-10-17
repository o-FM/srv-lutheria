package com.luthier.service.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.luthier.shared.errors.BadRequestException;
import java.util.Arrays;

/** Momento em que a foto da OS foi tirada (igual ao front). */
public enum PhotoTag {
  ENTRADA("entrada"),
  DIAGNOSTICO("diagnostico"),
  BANCADA("bancada"),
  FINALIZADO("finalizado"),
  DETALHE_DEFEITO("detalhe_defeito");

  private final String value;

  PhotoTag(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static PhotoTag fromValue(String value) {
    return Arrays.stream(values())
        .filter(e -> e.value.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new BadRequestException("Tipo de foto inválido: " + value));
  }
}
