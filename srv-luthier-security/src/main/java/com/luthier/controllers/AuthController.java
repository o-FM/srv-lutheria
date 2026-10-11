package com.luthier.controllers;

import com.luthier.domain.records.LoginRequest;
import com.luthier.domain.records.LoginResponse;
import com.luthier.services.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final TokenService tokenService;

  public AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
    this.authenticationManager = authenticationManager;
    this.tokenService = tokenService;
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {

    var authentication = authenticationManager.authenticate(
        UsernamePasswordAuthenticationToken.unauthenticated(loginRequest.email(), loginRequest.password())
    );

    var token = tokenService.generate(authentication);

    return ResponseEntity.ok(new LoginResponse(token, "Bearer", tokenService.expirationInSeconds()));
  }
}
