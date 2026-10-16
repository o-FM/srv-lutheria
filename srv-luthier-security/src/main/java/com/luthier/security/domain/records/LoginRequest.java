package com.luthier.security.domain.records;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login dos profissionais da luthieria: e-mail OU CPF (com ou sem máscara) + senha.
 * Aceita o campo antigo "email" por compatibilidade.
 */
public record LoginRequest(@NotBlank @Size(max = 254) @JsonAlias("email") String identifier,
                           @NotBlank @Size(min = 8, max = 128) String password) {

  @Override
  public String toString() {
    return "LoginRequest[identifier=***, password=***]";
  }
}
