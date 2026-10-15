package com.luthier.security;

import java.util.UUID;

/** Evento público: uma nova luthieria se cadastrou (ex.: notification envia boas-vindas). */
public record TenantRegistered(UUID tenantId, String tenantName, UUID ownerId, String ownerName,
                               String ownerEmail) {}
