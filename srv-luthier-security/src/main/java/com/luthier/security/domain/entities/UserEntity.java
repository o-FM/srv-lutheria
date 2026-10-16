package com.luthier.security.domain.entities;

import com.luthier.security.domain.enums.Profile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * Usuário interno de uma luthieria.
 *
 * tenant_id é coluna simples (sem @TenantId): o login acontece ANTES de sabermos o tenant, então
 * esta tabela não pode ser filtrada automaticamente. Quem consulta usuários dentro de uma
 * requisição autenticada filtra pelo tenant explicitamente (ver UserManagementServiceImpl).
 * E-mail é único na plataforma inteira porque é o identificador de login.
 */
@Entity
@Table(name = "USER_TB",
    indexes = @Index(name = "idx_user_tenant", columnList = "tenant_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_user_tenant_cpf", columnNames = {"tenant_id", "cpf"}))
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID uuid;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(length = 100)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(length = 11)
  private String cpf;

  @Column(length = 11)
  private String phone;

  @Column(name = "password_hash", nullable = false)
  private String password;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Profile profile;

  @Column(nullable = false)
  private boolean active = true;

  protected UserEntity() {}

  public UUID getUuid() { return uuid; }
  public UUID getTenantId() { return tenantId; }
  public String getName() { return name; }
  public String getEmail() { return email; }
  public String getCpf() { return cpf; }
  public String getPhone() { return phone; }
  public String getPassword() { return password; }
  public Profile getProfile() { return profile; }
  public boolean isActive() { return active; }

  public boolean belongsTo(UUID tenantId) { return this.tenantId.equals(tenantId); }

  public void changeProfile(Profile profile) { this.profile = profile; }
  public void activate() { this.active = true; }
  public void deactivate() { this.active = false; }

  public void changePassword(String encodedPassword) { this.password = encodedPassword; }
  public void changeEmail(String email) { this.email = email; }
  public void changePhone(String phone) { this.phone = phone; }

  public static UserEntity newUser(UUID tenantId, String name, String email, String cpf,
      String phone, String password, Profile profile) {
    var u = new UserEntity();
    u.tenantId = tenantId;
    u.name = name;
    u.email = email;
    u.cpf = cpf;
    u.phone = phone;
    u.password = password;
    u.profile = profile;
    u.active = true;
    return u;
  }
}
