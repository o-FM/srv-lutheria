package com.luthier.security.domain.records;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Autoatendimento do próprio usuário: qualquer alteração exige a senha atual. */
public record UpdateMyAccountRequest(
    @NotBlank @Size(max = 128) String currentPassword,
    @Email @Size(max = 254) String email,
    @Size(max = 20) String phone,
    @Size(min = 12, max = 128) String newPassword
) {

  @Override
  public String toString() {
    return "UpdateMyAccountRequest[email=" + email + ", currentPassword=***, newPassword=***]";
  }
}
