package com.luthier.configs;

import com.luthier.domain.entities.TenantEntity;
import com.luthier.domain.entities.UserEntity;
import com.luthier.domain.enums.Profile;
import com.luthier.domain.repositories.TenantRepository;
import com.luthier.domain.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolve o "ovo e a galinha": POST /users exige ADMIN, mas ainda não existe nenhum usuário.
 * Na subida, se não houver nenhum ADMIN, cria uma luthieria inicial e o seu ADMIN a partir da
 * configuração. Novas luthierias entram pelo POST /auth/register.
 */
public class AdminBootstrap implements ApplicationRunner {

  private static final Logger logger = LoggerFactory.getLogger(AdminBootstrap.class);

  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final PasswordEncoder passwordEncoder;
  private final String tenantName;
  private final String email;
  private final String password;

  public AdminBootstrap(UserRepository userRepository, TenantRepository tenantRepository,
      PasswordEncoder passwordEncoder, String tenantName, String email, String password) {
    this.userRepository = userRepository;
    this.tenantRepository = tenantRepository;
    this.passwordEncoder = passwordEncoder;
    this.tenantName = tenantName;
    this.email = email;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) throws Exception {
    if (userRepository.existsByProfile(Profile.ADMIN)) return;

    if (password == null || password.length() < 12) {
      throw new IllegalStateException("The password must be at least 12 characters long.");
    }

    var tenant = tenantRepository.save(TenantEntity.newTenant(tenantName, "luthieria-inicial", null));
    userRepository.save(UserEntity.newUser(tenant.getId(), "Administrador", email, null, null,
        passwordEncoder.encode(password), Profile.ADMIN));
    logger.warn("Initial admin account created ({}). Change the password as soon as possible.", email);
  }
}
