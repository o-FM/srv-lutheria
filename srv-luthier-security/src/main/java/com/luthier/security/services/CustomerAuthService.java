package com.luthier.security.services;

import com.luthier.security.domain.records.CustomerLoginRequest;
import com.luthier.security.domain.records.TokenResponse;

/** Login da área do cliente: só o código de acesso enviado pelo WhatsApp. */
public interface CustomerAuthService {

  TokenResponse login(CustomerLoginRequest request);
}
