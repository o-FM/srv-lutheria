package com.luthier.security.configs;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class PasswordEncoderConfig {

  private static final String ARGON2 = "argon2";
  private static final String BCRYPT = "bcrypt";

  @Bean
  public PasswordEncoder passwordEncoder() {
    var argon2 = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
    var bcrypt = new BCryptPasswordEncoder();

    var delegating = new DelegatingPasswordEncoder(ARGON2, Map.of(ARGON2, argon2, BCRYPT, bcrypt));
    delegating.setDefaultPasswordEncoderForMatches(bcrypt);

    return delegating;
  }
}
