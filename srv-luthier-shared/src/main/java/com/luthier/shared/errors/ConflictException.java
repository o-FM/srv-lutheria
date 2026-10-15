package com.luthier.shared.errors;

public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
