package com.luthier.services;

import com.luthier.domain.records.RegisterTenantRequest;
import com.luthier.domain.records.TokenResponse;

public interface TenantRegistrationService {

  /**
   * Cria a luthieria (tenant) e o usuário dono (ADMIN dela) numa única transação e já devolve a
   * sessão aberta, para o front entrar direto no app após o cadastro.
   */
  TokenResponse register(RegisterTenantRequest request);
}
