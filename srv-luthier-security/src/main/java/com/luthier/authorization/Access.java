package com.luthier.authorization;

public final class Access {

  public static final String ADMIN = "hasRole('" + Roles.ADMIN + "')";

  public static final String ADMIN_OR_LUTHIER = "hasAnyRole('" + Roles.ADMIN + "','" + Roles.LUTHIER + "')";

  public static final String ADMIN_OR_ATTENDANT = "hasAnyRole('" + Roles.ADMIN + "','" + Roles.ATTENDANT + "')";

  public static final String ADMIN_OR_STOCK_CLERK = "hasAnyRole('" + Roles.ADMIN + "','" + Roles.STOCK_CLERK + "')";

  private Access() {
  }
}