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

  /** Cliente ativo com este telefone (só dígitos), na luthieria atual. */
  Optional<CustomerSummary> findByPhone(String phoneDigits);

  /**
   * LGPD (portabilidade/acesso): todos os dados pessoais do cliente, SEM máscara, para entrega
   * ao próprio titular. Inclui clientes inativos.
   */
  Optional<CustomerExport> exportData(UUID customerId);

  record CustomerExport(UUID id, String name, String phone, String email, String cpf,
                        String address, String notes, boolean active,
                        java.time.Instant createdAt, java.util.List<InstrumentExport> instruments) {}

  record InstrumentExport(UUID id, String type, String brand, String model, String serialNumber,
                          String year, String finishColor, String conditionNotes) {}

  record CustomerSummary(UUID id, String name, String phone, String email) {}

  record InstrumentSummary(UUID id, UUID customerId, String type, String brand, String model) {}
}
