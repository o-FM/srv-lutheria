package com.luthier.domain.events;

import java.util.UUID;

/** Uma nova luthieria se cadastrou (ex.: notification envia boas-vindas). */
public record TenantRegistered(UUID tenantId, String tenantName, UUID ownerId, String ownerEmail) {}
