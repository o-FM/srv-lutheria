package com.luthier.notification.domain.records;

import com.luthier.notification.domain.entities.NotificationEntity;
import com.luthier.notification.domain.enums.Channel;
import com.luthier.notification.domain.enums.DeliveryStatus;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, Channel channel, DeliveryStatus status,
                                   String recipient, String subject, String body,
                                   String actionUrl, String relatedType, UUID relatedId,
                                   Instant createdAt, Instant sentAt, String error) {

  public static NotificationResponse from(NotificationEntity n) {
    return new NotificationResponse(n.getId(), n.getChannel(), n.getStatus(), n.getRecipient(),
        n.getSubject(), n.getBody(), n.getActionUrl(), n.getRelatedType(), n.getRelatedId(),
        n.getCreatedAt(), n.getSentAt(), n.getError());
  }
}
