package com.luthier.service;

import java.util.UUID;

/**
 * Evento público: o cliente aprovou o orçamento pelo link do WhatsApp (portal). Dispara o envio
 * do código de acesso à área do cliente.
 */
public record QuoteApprovedByCustomer(UUID tenantId, UUID orderId, int orderNumber,
                                      UUID customerId) {}
