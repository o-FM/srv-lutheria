package com.luthier.customer;

import java.util.UUID;

/**
 * Evento público (LGPD, direito à eliminação): os dados pessoais do cliente foram apagados.
 * Outros módulos que guardem cópias (ex.: notificações enviadas) devem apagá-las também.
 */
public record CustomerAnonymized(UUID tenantId, UUID customerId) {}
