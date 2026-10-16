package com.luthier.customer.domain.records;

import com.luthier.customer.domain.entities.CustomerEntity;
import com.luthier.shared.validation.Cpf;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Espelha o tipo Client do front. CPF sai SEMPRE mascarado (LGPD); instruments só vem preenchido
 * no detalhe (GET /customers/{id}), na listagem fica vazio.
 */
public record CustomerResponse(UUID id, String name, String phone, String email,
                               String maskedCpf, String address, String notes,
                               Instant createdAt, List<InstrumentResponse> instruments) {

  public static CustomerResponse from(CustomerEntity c) {
    return from(c, List.of());
  }

  public static CustomerResponse from(CustomerEntity c, List<InstrumentResponse> instruments) {
    return new CustomerResponse(c.getId(), c.getName(), formatPhone(c.getPhone()), c.getEmail(),
        Cpf.mask(c.getCpf()), c.getAddress(), c.getNotes(), c.getCreatedAt(), instruments);
  }

  /** Mesmo formato da máscara do front: (11) 98765-4321 / (11) 3456-7890. */
  static String formatPhone(String d) {
    if (d == null || d.length() < 10) return d;
    int split = d.length() == 11 ? 7 : 6;
    return "(" + d.substring(0, 2) + ") " + d.substring(2, split) + "-" + d.substring(split);
  }
}
