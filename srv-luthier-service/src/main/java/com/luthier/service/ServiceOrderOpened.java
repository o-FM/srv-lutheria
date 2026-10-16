package com.luthier.service;

import java.util.UUID;

/** Evento público: uma OS foi aberta (dispara o WhatsApp com o acesso do cliente). */
public record ServiceOrderOpened(UUID tenantId, UUID orderId, int orderNumber, UUID customerId,
                                 UUID instrumentId, String trackingToken) {}
