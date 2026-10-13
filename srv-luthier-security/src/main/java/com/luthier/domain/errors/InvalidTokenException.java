package com.luthier.domain.errors;

import org.springframework.http.HttpStatus;

/**
 * Token opaco (refresh ou reset) inválido, expirado, já usado ou revogado.
 * A mensagem é sempre a mesma de propósito: não revela ao cliente QUAL foi o motivo.
 */
public class InvalidTokenException extends RuntimeException {

  private final HttpStatus status;

  private InvalidTokenException(HttpStatus status) {
    super("Token inválido ou expirado");
    this.status = status;
  }

  /** Refresh token ruim -> 401 (o cliente precisa logar de novo). */
  public static InvalidTokenException refresh() {
    return new InvalidTokenException(HttpStatus.UNAUTHORIZED);
  }

  /** Token de reset ruim -> 400 (não é um problema de autenticação, e sim de dado inválido). */
  public static InvalidTokenException reset() {
    return new InvalidTokenException(HttpStatus.BAD_REQUEST);
  }

  public HttpStatus status() {
    return status;
  }
}
