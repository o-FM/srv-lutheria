package com.luthier.domain.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
    @NotBlank @Size(max = 2048) String refreshToken) {

  @Override
  public String toString() {
    return "RefreshRequest[refreshToken=***]";
  }
}
