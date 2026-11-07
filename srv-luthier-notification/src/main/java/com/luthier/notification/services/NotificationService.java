package com.luthier.notification.services;

import com.luthier.notification.domain.records.NotificationResponse;
import com.luthier.shared.authorization.Access;
import com.luthier.shared.web.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Caixa de saída da luthieria (tela "Mensagens"). Todo usuário interno entra, mas só o balcão
 * (ADMIN, LUTHIER, ATENDENTE) vê as mensagens aos clientes; os demais veem só alertas internos.
 */
public interface NotificationService {

  /** Fora do balcão, filtra sempre pelo canal interno; pedir outro canal responde 403. */
  @PreAuthorize(Access.ANY_STAFF)
  PageResponse<NotificationResponse> search(String channel, String status, Pageable pageable);

  /** WhatsApp enviado manualmente pelo balcão / alerta lido (404 para mensagem fora do alcance). */
  @PreAuthorize(Access.ANY_STAFF)
  NotificationResponse markSent(UUID id);
}
