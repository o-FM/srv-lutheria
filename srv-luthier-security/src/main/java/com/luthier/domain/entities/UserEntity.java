package com.luthier.domain.entities;

import com.luthier.domain.enums.Profile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "USER_TB")
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID uuid;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String password;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Profile profile;

  @Column(nullable = false)
  private boolean active = true;

  protected UserEntity() {}

  public UUID getUuid() { return uuid; }
  public String getEmail() { return email; }
  public String getPassword() { return password; }
  public Profile getProfile() { return profile; }
  public boolean isActive() { return active; }

  // TODO: RETIRAR APOS OS TESTES
  public static UserEntity newUser(String email, String password, Profile profile) {
    var u = new UserEntity();
    u.email = email;
    u.password = password;
    u.profile = profile;
    u.active = true;
    return u;
  }
}
