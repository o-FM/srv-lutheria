package com.luthier.customer.domain.entities;

import com.luthier.customer.domain.enums.InstrumentType;
import com.luthier.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/** Instrumento de um cliente (ficha técnica). Referencia o cliente por ID. */
@Entity
@Table(name = "INSTRUMENT_TB", indexes = {
    @Index(name = "idx_instrument_tenant_customer", columnList = "tenant_id, customer_id"),
    @Index(name = "idx_instrument_tenant_serial", columnList = "tenant_id, serial_number")
})
public class InstrumentEntity extends TenantScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InstrumentType type;

  @Column(nullable = false, length = 60)
  private String brand;

  @Column(nullable = false, length = 80)
  private String model;

  @Column(name = "serial_number", length = 60)
  private String serialNumber;

  @Column(name = "manufacture_year", length = 10)
  private String year;

  @Column(name = "finish_color", length = 60)
  private String finishColor;

  @Column(name = "strings_gauge", length = 30)
  private String stringsGauge;

  @Column(name = "tuning_preference", length = 60)
  private String tuningPreference;

  @Column(name = "action_height_12th_fret", length = 30)
  private String actionHeight12thFret;

  @Column(name = "condition_notes", length = 1000)
  private String conditionNotes;

  protected InstrumentEntity() {}

  public static InstrumentEntity newInstrument(UUID customerId, InstrumentData data) {
    var i = new InstrumentEntity();
    i.customerId = customerId;
    i.update(data);
    return i;
  }

  public void update(InstrumentData data) {
    this.type = data.type();
    this.brand = data.brand();
    this.model = data.model();
    this.serialNumber = data.serialNumber();
    this.year = data.year();
    this.finishColor = data.finishColor();
    this.stringsGauge = data.stringsGauge();
    this.tuningPreference = data.tuningPreference();
    this.actionHeight12thFret = data.actionHeight12thFret();
    this.conditionNotes = data.conditionNotes();
  }

  /** Dados editáveis da ficha técnica. */
  public record InstrumentData(InstrumentType type, String brand, String model,
                               String serialNumber, String year, String finishColor,
                               String stringsGauge, String tuningPreference,
                               String actionHeight12thFret, String conditionNotes) {}

  public UUID getId() { return id; }
  public UUID getCustomerId() { return customerId; }
  public InstrumentType getType() { return type; }
  public String getBrand() { return brand; }
  public String getModel() { return model; }
  public String getSerialNumber() { return serialNumber; }
  public String getYear() { return year; }
  public String getFinishColor() { return finishColor; }
  public String getStringsGauge() { return stringsGauge; }
  public String getTuningPreference() { return tuningPreference; }
  public String getActionHeight12thFret() { return actionHeight12thFret; }
  public String getConditionNotes() { return conditionNotes; }
}
