package com.luthier.customer.domain.records;

import com.luthier.validation.ValidCpf;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Formulário "Cadastrar Novo Cliente" do front. Telefone e CPF podem vir com máscara.
 * Usado no POST e no PUT (PUT substitui todos os campos).
 */
public record CustomerRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Pattern(regexp = "^\\D*(\\d\\D*){10,11}$", message = "Telefone deve ter DDD + número")
    String phone,
    @Email @Size(max = 254) String email,
    @ValidCpf String cpf,
    @Size(max = 255) String address,
    @Size(max = 1000) String notes
) {}
