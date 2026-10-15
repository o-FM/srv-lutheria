package com.luthier.security.configs;

import com.luthier.security.domain.repositories.TenantRepository;
import com.luthier.security.domain.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class AdminBootstrapConfig {

  @Bean
  AdminBootstrap adminBootstrap(UserRepository userRepository, TenantRepository tenantRepository,
      PasswordEncoder passwordEncoder,
      @Value("${luthieria.admin.luthieria:Luthieria Inicial}") String tenantName,
      @Value("${luthieria.admin.email:admin@luthieria.local}") String email,
      @Value("${luthieria.admin.senha}") String password) {
    return new AdminBootstrap(userRepository, tenantRepository, passwordEncoder, tenantName, email,
        password);
  }
}
