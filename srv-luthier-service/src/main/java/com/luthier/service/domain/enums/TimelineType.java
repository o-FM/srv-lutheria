package com.luthier.service.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.luthier.shared.errors.BadRequestException;
import java.util.Arrays;

/** Tipos de evento da linha do tempo (igual ao front). */
public enum TimelineType {
  STATUS_CHANGE("status_change"),
  QUOTE_SENT("quote_sent"),
  QUOTE_APPROVED("quote_approved"),
  QUOTE_REJECTED("quote_rejected"),
  SERVICE_DONE("service_done"),
  PAYMENT_RECEIVED("payment_received"),
  NOTE_ADDED("note_added"),
  PHOTO_ADDED("photo_added");

  private final String value;

  TimelineType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static TimelineType fromValue(String value) {
    return Arrays.stream(values())
        .filter(e -> e.value.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new BadRequestException("Valor inválido: " + value));
  }
}
