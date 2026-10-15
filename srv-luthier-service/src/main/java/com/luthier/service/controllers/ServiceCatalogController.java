package com.luthier.service.controllers;

import com.luthier.service.domain.records.ServiceCatalogRequest;
import com.luthier.service.domain.records.ServiceCatalogResponse;
import com.luthier.service.services.ServiceCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/service-catalog")
@Validated
public class ServiceCatalogController {

  private final ServiceCatalogService service;

  public ServiceCatalogController(ServiceCatalogService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ServiceCatalogResponse create(@Valid @RequestBody ServiceCatalogRequest request) {
    return service.create(request);
  }

  @GetMapping
  public List<ServiceCatalogResponse> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  public ServiceCatalogResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  public ServiceCatalogResponse update(@PathVariable UUID id,
      @Valid @RequestBody ServiceCatalogRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID id) {
    service.deactivate(id);
  }
}
