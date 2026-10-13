package com.luthier.configs;

import com.luthier.domain.entities.UserEntity;
import com.luthier.domain.enums.Profile;
import com.luthier.domain.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Resolve o "ovo e a galinha": POST /users exige ADMIN, mas ainda não existe nenhum usuário.
 * Na subida, se não houver nenhum ADMIN, cria o primeiro a partir da configuração.
 */
public class AdminBootstrap implements ApplicationRunner {

  private static final Logger logger = LoggerFactory.getLogger(AdminBootstrap.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final String email;
  private final String password;

  public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
      String email, String password) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.email = email;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (userRepository.existsByProfile(Profile.ADMIN)) return;

    if (password == null || password.length() < 12) {
      throw new IllegalStateException("The password must be at least 12 characters long.");
    }

    userRepository.save(UserEntity.newUser(email, passwordEncoder.encode(password), Profile.ADMIN));
    logger.warn("Initial admin account created ({}). Change the password as soon as possible.", email);
  }
}
