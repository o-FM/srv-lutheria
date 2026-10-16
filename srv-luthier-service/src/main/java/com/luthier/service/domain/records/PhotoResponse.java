package com.luthier.service.domain.records;

import com.luthier.service.domain.enums.PhotoTag;
import java.time.Instant;
import java.util.UUID;

/** Metadados da foto; a imagem sai em GET .../photos/{id}/content. */
public record PhotoResponse(UUID id, PhotoTag tag, String caption, Instant timestamp,
                            String user) {
}
