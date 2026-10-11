package com.luthier.domain.enums;

public enum Profile {
  ADMIN("ADMIN"),
  LUTHIER("LUTHIER"),
  ATTENDANT("ATENDENTE"),
  STOCK_CLERK("ESTOQUISTA");

  private final String value;

  Profile(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
