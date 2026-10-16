package com.luthier.security.controllers;

import com.luthier.security.domain.records.CustomerLoginRequest;
import com.luthier.security.domain.records.TokenResponse;
import com.luthier.security.services.CustomerAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Área do cliente: login só com o código de acesso recebido no WhatsApp (rota pública). */
@RestController
@RequestMapping("/auth/customer")
public class CustomerAuthController {

  private final CustomerAuthService service;

  public CustomerAuthController(CustomerAuthService service) {
    this.service = service;
  }

  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody CustomerLoginRequest request) {
    return service.login(request);
  }
}
