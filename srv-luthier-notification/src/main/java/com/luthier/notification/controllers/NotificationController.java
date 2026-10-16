package com.luthier.notification.controllers;

import com.luthier.notification.domain.records.NotificationResponse;
import com.luthier.notification.services.NotificationService;
import com.luthier.shared.web.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  /** ?channel=whatsapp&status=pendente → mensagens prontas para o balcão enviar. */
  @GetMapping
  public PageResponse<NotificationResponse> search(
      @RequestParam(required = false) String channel,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.search(channel, status, PageRequest.of(Math.max(page, 0),
        Math.clamp(size, 1, 100)));
  }

  @PostMapping("/{id}/sent")
  public NotificationResponse markSent(@PathVariable UUID id) {
    return service.markSent(id);
  }
}
