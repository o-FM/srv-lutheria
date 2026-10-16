package com.luthier.security.domain.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Login da área do cliente: só o código de acesso (ex.: K7M4-P9QX), com ou sem traço. */
public record CustomerLoginRequest(@NotBlank @Size(max = 20) String code) {

  @Override
  public String toString() {
    return "CustomerLoginRequest[code=***]";
  }
}
