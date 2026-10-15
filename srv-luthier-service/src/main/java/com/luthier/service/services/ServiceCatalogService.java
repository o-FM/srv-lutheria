package com.luthier.service.services;

import com.luthier.authorization.Access;
import com.luthier.service.domain.records.ServiceCatalogRequest;
import com.luthier.service.domain.records.ServiceCatalogResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/** Catálogo de serviços (preços de mão de obra) da luthieria do usuário logado. */
public interface ServiceCatalogService {

  @PreAuthorize(Access.ADMIN_OR_LUTHIER)
  ServiceCatalogResponse create(ServiceCatalogRequest request);

  @PreAuthorize(Access.ANY_STAFF)
  List<ServiceCatalogResponse> list();

  @PreAuthorize(Access.ANY_STAFF)
  ServiceCatalogResponse get(UUID id);

  @PreAuthorize(Access.ADMIN_OR_LUTHIER)
  ServiceCatalogResponse update(UUID id, ServiceCatalogRequest request);

  @PreAuthorize(Access.ADMIN_OR_LUTHIER)
  void deactivate(UUID id);
}
