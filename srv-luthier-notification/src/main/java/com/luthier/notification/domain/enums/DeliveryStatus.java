package com.luthier.notification.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DeliveryStatus {
  SENT("enviado"),
  FAILED("falhou"),
  /** Aguardando alguém da oficina enviar (WhatsApp) ou ler (alerta interno). */
  PENDING("pendente");

  private final String value;

  DeliveryStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
