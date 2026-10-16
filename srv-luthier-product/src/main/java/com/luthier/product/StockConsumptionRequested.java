package com.luthier.product;

import java.util.List;
import java.util.UUID;

/**
 * Pedido de baixa de estoque (ex.: OS concluída). Definido AQUI, no módulo de produtos, para que
 * quem consome peças (módulo de serviços) dependa de produtos e nunca o contrário — sem ciclo.
 * Processado de forma assíncrona e garantida (outbox do Spring Modulith).
 */
public record StockConsumptionRequested(UUID tenantId, UUID orderId, String reference,
                                        String requestedBy, List<Item> items) {

  public record Item(UUID productId, int quantity) {}
}
