package com.luthier.notification.services.impl;

import com.luthier.notification.domain.enums.Channel;
import com.luthier.notification.domain.enums.DeliveryStatus;
import com.luthier.notification.domain.records.NotificationResponse;
import com.luthier.notification.domain.repositories.NotificationRepository;
import com.luthier.notification.listeners.CustomerAccessListeners;
import com.luthier.notification.services.NotificationService;
import com.luthier.shared.errors.BadRequestException;
import com.luthier.shared.errors.NotFoundException;
import com.luthier.shared.web.PageResponse;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

  private final NotificationRepository repository;
  private final CustomerAccessListeners.Handlers customerAccess;

  public NotificationServiceImpl(NotificationRepository repository,
      CustomerAccessListeners.Handlers customerAccess) {
    this.repository = repository;
    this.customerAccess = customerAccess;
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<NotificationResponse> search(String channel, String status,
      Pageable pageable) {
    return PageResponse.of(repository.search(parseChannel(channel), parseStatus(status), pageable),
        NotificationResponse::from);
  }

  @Override
  public NotificationResponse markSent(UUID id) {
    var n = repository.findById(id)
        .orElseThrow(() -> new NotFoundException("Notification not found!"));
    n.markSent();
    return NotificationResponse.from(n);
  }

  @Override
  public NotificationResponse sendCustomerAccessCode(UUID customerId) {
    return NotificationResponse.from(customerAccess.resendCode(customerId));
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
