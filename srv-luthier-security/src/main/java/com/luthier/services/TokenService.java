package com.luthier.services;

import com.luthier.domain.entities.UserEntity;
import org.springframework.security.core.Authentication;

public interface TokenService {

  /** Gera o access token (JWT) de curta duração para o usuário. */
  String generate(UserEntity user);

  long expirationInSeconds();
}
