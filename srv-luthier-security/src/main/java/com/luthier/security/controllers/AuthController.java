package com.luthier.security.controllers;

import com.luthier.security.domain.records.ForgotPasswordRequest;
import com.luthier.security.domain.records.LoginRequest;
import com.luthier.security.domain.records.RefreshRequest;
import com.luthier.security.domain.records.RegisterTenantRequest;
import com.luthier.security.domain.records.ResetPasswordRequest;
import com.luthier.security.domain.records.TokenResponse;
import com.luthier.security.services.AuthService;
import com.luthier.security.services.PasswordRecoveryService;
import com.luthier.security.services.TenantRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

  private final AuthService authService;
  private final PasswordRecoveryService passwordRecoveryService;
  private final TenantRegistrationService tenantRegistrationService;

  public AuthController(AuthService authService, PasswordRecoveryService passwordRecoveryService,
      TenantRegistrationService tenantRegistrationService) {
    this.authService = authService;
    this.passwordRecoveryService = passwordRecoveryService;
    this.tenantRegistrationService = tenantRegistrationService;
  }

  /** Cadastro de uma nova luthieria + dono. Já devolve a sessão aberta. */
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public TokenResponse register(@Valid @RequestBody RegisterTenantRequest request) {
    return tenantRegistrationService.register(request);
  }

  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @PostMapping("/refresh")
  public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return authService.refresh(request);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody RefreshRequest request) {
    authService.logout(request);
  }

  /** 202 SEMPRE, exista o e-mail ou não. */
  @PostMapping("/forgot-password")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    passwordRecoveryService.requestReset(request);
  }

  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    passwordRecoveryService.reset(request);
  }
}
