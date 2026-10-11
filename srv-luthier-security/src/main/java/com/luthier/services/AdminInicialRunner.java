package com.luthier.services;

import com.luthier.domain.entities.UserEntity;
import com.luthier.domain.enums.Profile;
import com.luthier.domain.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class AdminInicialRunner implements ApplicationRunner {

  private final UserRepository repository;
  private final PasswordEncoder encoder;
  private final String email;
  private final String senha;

  AdminInicialRunner(UserRepository repository,
      PasswordEncoder encoder,
      @Value("${luthieria.admin.email:}") String email,
      @Value("${luthieria.admin.senha:}") String senha) {
    this.repository = repository;
    this.encoder = encoder;
    this.email = email;
    this.senha = senha;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (email.isBlank() || senha.isBlank() || repository.count() > 0) {
      return;
    }
    repository.save(UserEntity.newUser(email, encoder.encode(senha), Profile.ADMIN));
  }
}
