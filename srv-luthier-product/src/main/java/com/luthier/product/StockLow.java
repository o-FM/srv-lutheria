package com.luthier.product;

import java.util.UUID;

/** Evento público: após uma movimentação o saldo ficou no mínimo ou abaixo (alerta do "Hoje"). */
public record StockLow(UUID tenantId, UUID productId, String name, int currentQuantity,
                       int minQuantity) {}
