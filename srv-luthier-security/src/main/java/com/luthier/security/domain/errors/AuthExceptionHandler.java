package com.luthier.security.domain.errors;

import com.luthier.security.controllers.AuthController;
import com.luthier.security.controllers.CustomerAuthController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Restrito ao AuthController (assignableTypes) para não interferir no tratamento padrão do Spring
 * Security nos demais endpoints (401/403 via ExceptionTranslationFilter).
 */
@RestControllerAdvice(assignableTypes = {AuthController.class, CustomerAuthController.class})
public class AuthExceptionHandler {

  /**
   * BadCredentialsException, DisabledException, LockedException... todas viram a MESMA resposta.
   * Se devolvêssemos "usuário desativado" separado de "senha errada", atacantes poderiam descobrir
   * quais e-mails existem (enumeração de usuários).
   */
  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
    return problem(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
  }

  @ExceptionHandler(InvalidTokenException.class)
  ResponseEntity<ProblemDetail> handleInvalidToken(InvalidTokenException ex) {
    return problem(ex.status(), ex.getMessage());
  }

  private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
    return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
  }
}
