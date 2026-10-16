package com.luthier.notification.services;

import com.luthier.notification.domain.records.NotificationResponse;
import com.luthier.shared.authorization.Access;
import com.luthier.shared.web.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

/** Caixa de saída da luthieria (tela "Mensagens"). */
public interface NotificationService {

  @PreAuthorize(Access.ANY_STAFF)
  PageResponse<NotificationResponse> search(String channel, String status, Pageable pageable);

  /** WhatsApp enviado manualmente pelo balcão / alerta lido. */
  @PreAuthorize(Access.ANY_STAFF)
  NotificationResponse markSent(UUID id);
}
