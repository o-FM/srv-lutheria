package com.luthier.security;

import java.util.Optional;
import java.util.UUID;

/** API pública: dados da luthieria para mensagens e links (nome e identificador na URL). */
public interface WorkshopDirectory {

  Optional<Workshop> find(UUID tenantId);

  record Workshop(UUID id, String name, String slug) {}
}
