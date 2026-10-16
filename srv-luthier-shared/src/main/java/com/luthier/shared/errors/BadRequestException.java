package com.luthier.shared.errors;

/** Dado válido no formato, mas que viola uma regra de negócio simples (400). */
public class BadRequestException extends RuntimeException {

  public BadRequestException(String message) {
    super(message);
  }
}
