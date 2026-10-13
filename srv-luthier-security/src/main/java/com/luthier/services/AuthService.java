package com.luthier.services;

import com.luthier.domain.records.LoginRequest;
import com.luthier.domain.records.RefreshRequest;
import com.luthier.domain.records.TokenResponse;

public interface AuthService {

  /** Valida e-mail/senha e devolve access token (JWT) + refresh token. */
  TokenResponse login(LoginRequest request);

  /** Troca um refresh token válido por um par novo (rotação). O token antigo morre. */
  TokenResponse refresh(RefreshRequest request);

  /** Encerra a sessão (revoga toda a família do refresh token). Idempotente. */
  void logout(RefreshRequest request);
}

