package com.luthier.service.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.luthier.shared.errors.BadRequestException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida da OS como máquina de estados (valores iguais ao front):
 *
 * recebido → diagnostico → orcamento → aguardando_aprovacao → em_execucao → pronto → entregue
 *                              ↑                 │ (rejeitado)
 *                              └─────────────────┘
 * cancelado: possível até em_execucao (depois disso as peças já foram baixadas do estoque).
 */
public enum OrderStatus {
  RECEBIDO("recebido", "Recebido"),
  DIAGNOSTICO("diagnostico", "Em diagnóstico"),
  ORCAMENTO("orcamento", "Orçamento"),
  AGUARDANDO_APROVACAO("aguardando_aprovacao", "Aguardando aprovação"),
  EM_EXECUCAO("em_execucao", "Em execução"),
  PRONTO("pronto", "Pronto para retirada"),
  ENTREGUE("entregue", "Entregue"),
  CANCELADO("cancelado", "Cancelado");

  private final String value;
  private final String label;

  OrderStatus(String value, String label) {
    this.value = value;
    this.label = label;
  }

  public Set<OrderStatus> next() {
    return switch (this) {
      case RECEBIDO -> EnumSet.of(DIAGNOSTICO, ORCAMENTO, CANCELADO);
      case DIAGNOSTICO -> EnumSet.of(ORCAMENTO, CANCELADO);
      case ORCAMENTO -> EnumSet.of(AGUARDANDO_APROVACAO, CANCELADO);
      case AGUARDANDO_APROVACAO -> EnumSet.of(EM_EXECUCAO, ORCAMENTO, CANCELADO);
      case EM_EXECUCAO -> EnumSet.of(PRONTO, CANCELADO);
      case PRONTO -> EnumSet.of(ENTREGUE);
      case ENTREGUE, CANCELADO -> EnumSet.noneOf(OrderStatus.class);
    };
  }

  public boolean canGoTo(OrderStatus target) {
    return next().contains(target);
  }

  /** Itens (serviços/peças) só mudam antes do orçamento ser enviado. */
  public boolean isEditable() {
    return this == RECEBIDO || this == DIAGNOSTICO || this == ORCAMENTO;
  }

  public boolean isOpen() {
    return this != ENTREGUE && this != CANCELADO;
  }

  public String getLabel() {
    return label;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static OrderStatus fromValue(String value) {
    return Arrays.stream(values())
        .filter(e -> e.value.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new BadRequestException("Status inválido: " + value));
  }
}
