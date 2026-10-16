package com.luthier.shared.authorization;

public final class Access {

  public static final String ADMIN = "hasRole('" + Roles.ADMIN + "')";

  public static final String ADMIN_OR_LUTHIER = "hasAnyRole('" + Roles.ADMIN + "','" + Roles.LUTHIER + "')";

  public static final String ADMIN_OR_ATTENDANT = "hasAnyRole('" + Roles.ADMIN + "','" + Roles.ATTENDANT + "')";

  public static final String ADMIN_OR_STOCK_CLERK = "hasAnyRole('" + Roles.ADMIN + "','" + Roles.STOCK_CLERK + "')";

  /** Balcão e bancada: cadastro de clientes e instrumentos. */
  public static final String CUSTOMER_DESK = "hasAnyRole('" + Roles.ADMIN + "','" + Roles.LUTHIER
      + "','" + Roles.ATTENDANT + "')";

  /** Escrita no estoque (o front libera estoque ao luthier/dono). */
  public static final String STOCK_WRITE = "hasAnyRole('" + Roles.ADMIN + "','" + Roles.LUTHIER
      + "','" + Roles.STOCK_CLERK + "')";

  /** Qualquer usuário interno da luthieria. */
  public static final String ANY_STAFF = "hasAnyRole('" + Roles.ADMIN + "','" + Roles.LUTHIER
      + "','" + Roles.ATTENDANT + "','" + Roles.STOCK_CLERK + "')";

  private Access() {
  }
}
