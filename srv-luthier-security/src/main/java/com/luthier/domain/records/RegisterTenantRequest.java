package com.luthier.domain.records;

import com.luthier.validation.ValidCpf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cadastro self-service de uma nova luthieria + o usuário dono (ADMIN dessa luthieria). */
public record RegisterTenantRequest(
    @NotNull @Valid Workshop workshop,
    @NotNull @Valid Owner owner
) {

  public record Workshop(
      @NotBlank @Size(max = 120) String name,
      /** CNPJ (14) ou CPF (11), com ou sem máscara. */
      @Pattern(regexp = "^[\\d./-]{11,18}$", message = "Documento inválido") String document) {}

  public record Owner(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 128) String password,
      @ValidCpf String cpf,
      @Size(max = 20) String phone) {

    @Override
    public String toString() {
      return "Owner[email=" + email + ", password=***]";
    }
  }
}
