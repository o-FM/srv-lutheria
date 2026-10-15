package com.luthier.service.services.impl;

import com.luthier.shared.errors.ConflictException;
import com.luthier.shared.errors.NotFoundException;
import com.luthier.service.domain.entities.ServiceCatalogEntity;
import com.luthier.service.domain.records.ServiceCatalogRequest;
import com.luthier.service.domain.records.ServiceCatalogResponse;
import com.luthier.service.domain.repositories.ServiceCatalogRepository;
import com.luthier.service.services.ServiceCatalogService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServiceCatalogServiceImpl implements ServiceCatalogService {

  private final ServiceCatalogRepository repository;

  public ServiceCatalogServiceImpl(ServiceCatalogRepository repository) {
    this.repository = repository;
  }

  @Override
  public ServiceCatalogResponse create(ServiceCatalogRequest request) {
    var title = request.title().strip();
    if (repository.existsByTitleIgnoreCase(title)) {
      throw new ConflictException("Service already registered");
    }
    var service = repository.save(ServiceCatalogEntity.newService(title,
        blankToNull(request.description()), request.laborPrice(), request.estimatedDays()));
    return ServiceCatalogResponse.from(service);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ServiceCatalogResponse> list() {
    return repository.findAllByActiveTrueOrderByTitleAsc().stream()
        .map(ServiceCatalogResponse::from)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ServiceCatalogResponse get(UUID id) {
    return ServiceCatalogResponse.from(find(id));
  }

  @Override
  public ServiceCatalogResponse update(UUID id, ServiceCatalogRequest request) {
    var service = find(id);
    var title = request.title().strip();
    if (repository.existsByTitleIgnoreCaseAndIdNot(title, id)) {
      throw new ConflictException("Service already registered");
    }
    service.update(title, blankToNull(request.description()), request.laborPrice(),
        request.estimatedDays());
    return ServiceCatalogResponse.from(service);
  }

  @Override
  public void deactivate(UUID id) {
    find(id).deactivate();
  }

  private ServiceCatalogEntity find(UUID id) {
    return repository.findByIdAndActiveTrue(id)
        .orElseThrow(() -> new NotFoundException("Service not found!"));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
