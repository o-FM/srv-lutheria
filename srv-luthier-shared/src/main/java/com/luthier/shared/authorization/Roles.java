package com.luthier.shared.authorization;

public final class Roles {

  public static final String ADMIN = "ADMIN";
  public static final String LUTHIER = "LUTHIER";
  public static final String ATTENDANT = "ATENDENTE";
  public static final String STOCK_CLERK = "ESTOQUISTA";
  /** Cliente da luthieria (área do cliente, acesso por código enviado no WhatsApp). */
  public static final String CUSTOMER = "CLIENTE";

  private Roles() {
  }
}
