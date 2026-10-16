package com.luthier.security.services;

import com.luthier.security.domain.entities.UserEntity;
import org.springframework.security.core.Authentication;

public interface TokenService {

  /** Gera o access token (JWT) de curta duração para o usuário. */
  String generate(UserEntity user);

  long expirationInSeconds();

  /** JWT da área do cliente: papel CLIENTE, tenant (tid) e cliente (cid). Sem refresh token. */
  String generateForCustomer(java.util.UUID tenantId, java.util.UUID customerId, String phone);

  long customerExpirationInSeconds();
}
