package com.luthier.notification.controllers;

import com.luthier.notification.domain.records.NotificationResponse;
import com.luthier.notification.services.NotificationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Botão "Enviar código de acesso" na ficha do cliente. */
@RestController
public class CustomerAccessCodeController {

  private final NotificationService service;

  public CustomerAccessCodeController(NotificationService service) {
    this.service = service;
  }

  @PostMapping("/customers/{id}/access-code")
  public NotificationResponse send(@PathVariable UUID id) {
    return service.sendCustomerAccessCode(id);
  }
}
