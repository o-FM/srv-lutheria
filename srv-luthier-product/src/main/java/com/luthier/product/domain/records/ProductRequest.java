package com.luthier.product.domain.records;

import com.luthier.product.domain.enums.ProductCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Cadastro/edição de item de estoque. O saldo NÃO é editado aqui: use initialQuantity no
 * cadastro (gera uma ENTRADA) e POST /products/{id}/movements depois.
 */
public record ProductRequest(
    @NotBlank @Size(max = 120) String name,
    @NotNull ProductCategory category,
    @Size(max = 60) String brand,
    @NotBlank @Size(max = 40) String sku,
    @Size(max = 40) String barcode,
    @NotBlank @Pattern(regexp = "un|jogo|m|ml|g", message = "Unidade deve ser un, jogo, m, ml ou g")
    String unit,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal salePrice,
    @PositiveOrZero int minQuantity,
    @PositiveOrZero Integer initialQuantity,
    @Size(max = 60) String location
) {}
