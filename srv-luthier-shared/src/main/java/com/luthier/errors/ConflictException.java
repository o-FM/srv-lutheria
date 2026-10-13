package com.luthier.errors;

public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
