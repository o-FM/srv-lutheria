package com.luthier.security.domain.records;

import com.luthier.security.domain.entities.AuditLogEntity;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(UUID id, Instant timestamp, String actor, String action,
                               String resourceType, String resourceId, String details,
                               String ip) {

  public static AuditLogResponse from(AuditLogEntity a) {
    return new AuditLogResponse(a.getId(), a.getCreatedAt(), a.getActor(), a.getAction(),
        a.getResourceType(), a.getResourceId(), a.getDetails(), a.getIp());
  }
}
