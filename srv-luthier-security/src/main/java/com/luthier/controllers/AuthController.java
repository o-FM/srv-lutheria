package com.luthier.controllers;

import com.luthier.domain.records.ForgotPasswordRequest;
import com.luthier.domain.records.LoginRequest;
import com.luthier.domain.records.RefreshRequest;
import com.luthier.domain.records.ResetPasswordRequest;
import com.luthier.domain.records.TokenResponse;
import com.luthier.services.AuthService;
import com.luthier.services.PasswordRecoveryService;
import com.luthier.services.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

  public AuthController(AuthService authService, PasswordRecoveryService passwordRecoveryService) {
    this.authService = authService;
    this.passwordRecoveryService = passwordRecoveryService;
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
