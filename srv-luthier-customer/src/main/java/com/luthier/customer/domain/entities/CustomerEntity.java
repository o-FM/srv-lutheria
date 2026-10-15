package com.luthier.customer.domain.entities;

import com.luthier.shared.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/** Cliente de uma luthieria. CPF é único DENTRO da luthieria (outra pode ter o mesmo cliente). */
@Entity
@Table(name = "CUSTOMER_TB",
    uniqueConstraints = @UniqueConstraint(name = "uk_customer_tenant_cpf",
        columnNames = {"tenant_id", "cpf"}),
    indexes = {
        @Index(name = "idx_customer_tenant_name", columnList = "tenant_id, name"),
        @Index(name = "idx_customer_tenant_phone", columnList = "tenant_id, phone")
    })
public class CustomerEntity extends TenantScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 100)
  private String name;

  /** Só dígitos (DDD + número). */
  @Column(nullable = false, length = 11)
  private String phone;

  @Column(length = 254)
  private String email;

  /** Só dígitos. Nunca sai completo na API (LGPD). */
  @Column(length = 11)
  private String cpf;

  @Column(length = 255)
  private String address;

  @Column(length = 1000)
  private String notes;

  @Column(nullable = false)
  private boolean active = true;

  protected CustomerEntity() {}

  public static CustomerEntity newCustomer(String name, String phone, String email, String cpf,
      String address, String notes) {
    var c = new CustomerEntity();
    c.update(name, phone, email, cpf, address, notes);
    c.active = true;
    return c;
  }

  public void update(String name, String phone, String email, String cpf, String address,
      String notes) {
    this.name = name;
    this.phone = phone;
    this.email = email;
    this.cpf = cpf;
    this.address = address;
    this.notes = notes;
  }

  public void deactivate() { this.active = false; }

  public UUID getId() { return id; }
  public String getName() { return name; }
  public String getPhone() { return phone; }
  public String getEmail() { return email; }
  public String getCpf() { return cpf; }
  public String getAddress() { return address; }
  public String getNotes() { return notes; }
  public boolean isActive() { return active; }
}
