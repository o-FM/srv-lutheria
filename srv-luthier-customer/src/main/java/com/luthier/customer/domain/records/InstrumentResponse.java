package com.luthier.customer.domain.records;

import com.luthier.customer.domain.entities.InstrumentEntity;
import com.luthier.customer.domain.enums.InstrumentType;
import java.time.Instant;
import java.util.UUID;

public record InstrumentResponse(UUID id, UUID clientId, InstrumentType type, String brand,
                                 String model, String serialNumber, String year,
                                 String finishColor, String stringsGauge,
                                 String tuningPreference, String actionHeight12thFret,
                                 String conditionNotes, Instant createdAt) {

  public static InstrumentResponse from(InstrumentEntity i) {
    return new InstrumentResponse(i.getId(), i.getCustomerId(), i.getType(), i.getBrand(),
        i.getModel(), i.getSerialNumber(), i.getYear(), i.getFinishColor(), i.getStringsGauge(),
        i.getTuningPreference(), i.getActionHeight12thFret(), i.getConditionNotes(),
        i.getCreatedAt());
  }
}
