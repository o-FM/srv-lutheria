package com.luthier.service.domain.records;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ServiceCatalogRequest(
    @NotBlank @Size(max = 120) String title,
    @Size(max = 1000) String description,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal laborPrice,
    @Positive Integer estimatedDays
) {}
