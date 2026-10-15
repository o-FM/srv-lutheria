package com.luthier.customer;

import java.util.UUID;

/** Evento público: um cliente foi cadastrado numa luthieria. */
public record CustomerRegistered(UUID tenantId, UUID customerId, String name) {}
