package com.luthier.security.services;

import com.luthier.security.domain.records.CustomerCodeRequest;
import com.luthier.security.domain.records.CustomerLoginRequest;
import com.luthier.security.domain.records.TokenResponse;

/** Login da área do cliente por código de uso único (enviado pelo WhatsApp). */
public interface CustomerAuthService {

  /** Pede um novo código. Sempre "aceito", exista o cliente ou não (anti-enumeração). */
  void requestCode(CustomerCodeRequest request);

  TokenResponse login(CustomerLoginRequest request);
}
