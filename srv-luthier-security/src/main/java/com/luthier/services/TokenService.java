package com.luthier.services;

import org.springframework.security.core.Authentication;

public interface TokenService {

  String generate(Authentication authentication);
  long expirationInSeconds();
}
