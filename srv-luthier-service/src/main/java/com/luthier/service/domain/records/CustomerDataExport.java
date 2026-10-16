package com.luthier.service.domain.records;

import com.luthier.customer.CustomerQueries.CustomerExport;
import java.time.Instant;
import java.util.List;

/** Exportação de dados do titular (portabilidade/acesso). Sem máscaras: é para o próprio titular. */
public record CustomerDataExport(Instant generatedAt, String workshopNotice,
                                 CustomerExport customer, List<OrderResponse> serviceOrders) {}
