package com.luthier.notification.services.impl;

import com.luthier.notification.domain.enums.Channel;
import com.luthier.notification.domain.enums.DeliveryStatus;
import com.luthier.notification.domain.records.NotificationResponse;
import com.luthier.notification.domain.repositories.NotificationRepository;
import com.luthier.notification.services.NotificationService;
import com.luthier.shared.authorization.Roles;
import com.luthier.shared.errors.BadRequestException;
import com.luthier.shared.errors.NotFoundException;
import com.luthier.shared.web.PageResponse;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mensagens ao cliente (e-mail, WhatsApp) trazem contato e o link do portal, cujo token permite
 * responder o orçamento. Por isso só o balcão (ADMIN, LUTHIER, ATENDENTE), que já atende o
 * cliente e responde orçamentos, as enxerga. Os demais papéis (ESTOQUISTA) veem apenas os
 * alertas internos (NOT-01).
 */
@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

  private static final Set<String> DESK_AUTHORITIES = Set.of(
      "ROLE_" + Roles.ADMIN, "ROLE_" + Roles.LUTHIER, "ROLE_" + Roles.ATTENDANT);

  private final NotificationRepository repository;

  public NotificationServiceImpl(NotificationRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<NotificationResponse> search(String channel, String status,
      Pageable pageable) {
    var requested = parseChannel(channel);
    var visible = isDesk() ? requested : internalOnly(requested);
    return PageResponse.of(repository.search(visible, parseStatus(status), pageable),
        NotificationResponse::from);
  }

  @Override
  public NotificationResponse markSent(UUID id) {
    var n = repository.findById(id)
        .filter(found -> isDesk() || found.getChannel() == Channel.INTERNAL)
        .orElseThrow(() -> new NotFoundException("Notification not found!"));
    n.markSent();
    return NotificationResponse.from(n);
  }

  /** Fora do balcão o filtro é sempre o canal interno; pedir outro canal é acesso negado. */
  private static Channel internalOnly(Channel requested) {
    if (requested != null && requested != Channel.INTERNAL) {
      throw new AccessDeniedException("Somente o balcão acessa as mensagens aos clientes");
    }
    return Channel.INTERNAL;
  }

  private static boolean isDesk() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(DESK_AUTHORITIES::contains);
  }

  private static Channel parseChannel(String value) {
    if (value == null || value.isBlank()) return null;
    return Arrays.stream(Channel.values())
        .filter(c -> c.getValue().equalsIgnoreCase(value) || c.name().equalsIgnoreCase(value))
        .findFirst().orElseThrow(() -> new BadRequestException("Canal inválido: " + value));
  }

  private static DeliveryStatus parseStatus(String value) {
    if (value == null || value.isBlank()) return null;
    return Arrays.stream(DeliveryStatus.values())
        .filter(c -> c.getValue().equalsIgnoreCase(value) || c.name().equalsIgnoreCase(value))
        .findFirst().orElseThrow(() -> new BadRequestException("Status inválido: " + value));
  }
}
