package com.luthier.security.services;

import com.luthier.security.domain.records.ForgotPasswordRequest;
import com.luthier.security.domain.records.ResetPasswordRequest;

public interface PasswordRecoveryService {

  /** Nunca revela se o e-mail existe: retorna normalmente em qualquer caso. */
  void requestReset(ForgotPasswordRequest request);

  /** Consome o token (uso único), troca a senha e derruba todas as sessões do usuário. */
  void reset(ResetPasswordRequest request);
}

