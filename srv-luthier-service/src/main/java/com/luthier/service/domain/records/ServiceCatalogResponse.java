package com.luthier.service.domain.records;

import com.luthier.service.domain.entities.ServiceCatalogEntity;
import java.math.BigDecimal;
import java.util.UUID;

public record ServiceCatalogResponse(UUID id, String title, String description,
                                     BigDecimal laborPrice, Integer estimatedDays) {

  public static ServiceCatalogResponse from(ServiceCatalogEntity s) {
    return new ServiceCatalogResponse(s.getId(), s.getTitle(), s.getDescription(),
        s.getLaborPrice(), s.getEstimatedDays());
  }
}
