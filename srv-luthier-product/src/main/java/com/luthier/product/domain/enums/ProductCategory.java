package com.luthier.product.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.luthier.shared.errors.BadRequestException;
import java.util.Arrays;

/** Igual ao StockCategory do front. */
public enum ProductCategory {
  PECAS("pecas"),
  CORDAS("cordas"),
  MADEIRAS("madeiras"),
  INSUMOS("insumos");

  private final String value;

  ProductCategory(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static ProductCategory fromValue(String value) {
    return Arrays.stream(values())
        .filter(c -> c.value.equalsIgnoreCase(value) || c.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new BadRequestException("Categoria inválida: " + value));
  }
}
