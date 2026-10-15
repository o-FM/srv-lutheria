package com.luthier.customer;

import java.util.Optional;
import java.util.UUID;

/**
 * API pública do módulo de clientes para outros módulos (ex.: ordens de serviço).
 * Outros módulos NUNCA acessam entidades/repositórios daqui: só estes records, por ID.
 * Sempre no escopo da luthieria (tenant) atual.
 */
public interface CustomerQueries {

  boolean exists(UUID customerId);

  Optional<CustomerSummary> findCustomer(UUID customerId);

  Optional<InstrumentSummary> findInstrument(UUID instrumentId);

  long countActive();

  record CustomerSummary(UUID id, String name, String phone, String email) {}

  record InstrumentSummary(UUID id, UUID customerId, String type, String brand, String model) {}
}
