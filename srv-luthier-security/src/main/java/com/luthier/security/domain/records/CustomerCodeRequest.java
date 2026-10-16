package com.luthier.security.domain.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerCodeRequest(
    @NotBlank @Size(max = 60) String workshop,
    @NotBlank @Pattern(regexp = "^\\D*(\\d\\D*){10,11}$", message = "Telefone deve ter DDD + número")
    String phone) {}
