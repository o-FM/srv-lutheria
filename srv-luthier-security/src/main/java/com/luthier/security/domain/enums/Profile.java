package com.luthier.security.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import com.luthier.shared.authorization.Roles;

public enum Profile {
  ADMIN(Roles.ADMIN),
  LUTHIER(Roles.LUTHIER),
  ATTENDANT(Roles.ATTENDANT),
  STOCK_CLERK(Roles.STOCK_CLERK);

  private final String value;

  Profile(String value) {
    this.value = value;
  }

  /** Nome do papel (sem ROLE_). Também é o valor exposto na API, igual ao claim "roles" do JWT. */
  @JsonValue
  public String getValue() {
    return value;
  }
}
