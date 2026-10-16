package com.luthier.security.controllers;

import com.luthier.security.domain.records.CustomerCodeRequest;
import com.luthier.security.domain.records.CustomerLoginRequest;
import com.luthier.security.domain.records.TokenResponse;
import com.luthier.security.services.CustomerAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Área do cliente: login por telefone + código recebido no WhatsApp (rotas públicas). */
@RestController
@RequestMapping("/auth/customer")
public class CustomerAuthController {

  private final CustomerAuthService service;

  public CustomerAuthController(CustomerAuthService service) {
    this.service = service;
  }

  /** 202 sempre, exista o telefone ou não. */
  @PostMapping("/request-code")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void requestCode(@Valid @RequestBody CustomerCodeRequest request) {
    service.requestCode(request);
  }

  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody CustomerLoginRequest request) {
    return service.login(request);
  }
}
