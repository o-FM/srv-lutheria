package com.luthier.service.controllers;

import com.luthier.service.domain.records.CustomerDataExport;
import com.luthier.service.services.PrivacyService;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/privacy")
public class PrivacyController {

  private final PrivacyService privacy;

  public PrivacyController(PrivacyService privacy) {
    this.privacy = privacy;
  }

  /** Download JSON com tudo que a luthieria guarda sobre o cliente. */
  @GetMapping("/customers/{id}/export")
  public ResponseEntity<CustomerDataExport> export(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dados-cliente-" + id + ".json\"")
        .body(privacy.exportCustomer(id));
  }
}
