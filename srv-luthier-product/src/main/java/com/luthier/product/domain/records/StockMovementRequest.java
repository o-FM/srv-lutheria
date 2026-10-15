package com.luthier.product.domain.records;

import com.luthier.product.domain.enums.MovementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * ENTRADA/SAIDA: quantity > 0 é o quanto entra/sai.
 * AJUSTE: quantity é o saldo contado no inventário (pode ser 0).
 */
public record StockMovementRequest(
    @NotNull MovementType type,
    @NotNull @PositiveOrZero Integer quantity,
    @NotBlank @Size(max = 255) String reason,
    UUID orderId
) {}
