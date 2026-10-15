package com.luthier.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evento público: a OS mudou de status (inclui envio/resposta de orçamento). Valores de status
 * iguais aos da API ("pronto", "aguardando_aprovacao"...). Usado por notificações.
 */
public record ServiceOrderStatusChanged(UUID tenantId, UUID orderId, int orderNumber,
                                        UUID customerId, String from, String to,
                                        BigDecimal grandTotal, BigDecimal balance,
                                        String trackingToken) {}
