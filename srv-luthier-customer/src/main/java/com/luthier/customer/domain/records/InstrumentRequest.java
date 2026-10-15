package com.luthier.customer.domain.records;

import com.luthier.customer.domain.enums.InstrumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Formulário "Cadastrar Instrumento" do front (e passo 2 do wizard de OS). */
public record InstrumentRequest(
    @NotNull InstrumentType type,
    @NotBlank @Size(max = 60) String brand,
    @NotBlank @Size(max = 80) String model,
    @Size(max = 60) String serialNumber,
    @Size(max = 10) String year,
    @Size(max = 60) String finishColor,
    @Size(max = 30) String stringsGauge,
    @Size(max = 60) String tuningPreference,
    @Size(max = 30) String actionHeight12thFret,
    @Size(max = 1000) String conditionNotes
) {}
