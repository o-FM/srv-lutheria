package com.luthier.notification.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Channel {
  EMAIL("email"),
  /** Mensagem pronta + link wa.me: o balcão envia em 1 toque (tela "Mensagens" do front). */
  WHATSAPP("whatsapp"),
  /** Alerta interno da oficina (ex.: estoque baixo). */
  INTERNAL("interno");

  private final String value;

  Channel(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
