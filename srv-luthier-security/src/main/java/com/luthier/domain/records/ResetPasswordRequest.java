package com.luthier.domain.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank @Size(max = 256) String token,
    @NotBlank @Size(min = 12, max = 128) String newPassword) {

  /** Evita vazar token/senha caso o objeto seja logado por engano. */
  @Override
  public String toString() {
    return "ResetPasswordRequest[token=***, newPassword=***]";
  }
}
