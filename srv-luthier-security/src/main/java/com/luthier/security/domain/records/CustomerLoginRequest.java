package com.luthier.security.domain.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Login da área do cliente: oficina (slug do link) + telefone + código recebido no WhatsApp. */
public record CustomerLoginRequest(
    @NotBlank @Size(max = 60) String workshop,
    @NotBlank @Pattern(regexp = "^\\D*(\\d\\D*){10,11}$", message = "Telefone deve ter DDD + número")
    String phone,
    @NotBlank @Pattern(regexp = "\\d{6}", message = "Código deve ter 6 dígitos") String code) {

  @Override
  public String toString() {
    return "CustomerLoginRequest[workshop=" + workshop + ", code=***]";
  }
}
