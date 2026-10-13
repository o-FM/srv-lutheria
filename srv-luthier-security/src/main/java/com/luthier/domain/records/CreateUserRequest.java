package com.luthier.domain.records;

import com.luthier.domain.enums.Profile;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12, max = 128) String password,
    @Pattern(regexp = "\\d{11}") String cpf,
    @NotNull Profile profile
) {

  @Override
  public String toString() {
    return "CreateUserRequest[email=" + email + ", profile=" + profile + ", password=***]";
  }
}
