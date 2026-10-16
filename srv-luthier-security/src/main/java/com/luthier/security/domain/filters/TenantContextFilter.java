package com.luthier.security.domain.filters;

import com.luthier.shared.tenancy.CustomerContext;
import com.luthier.shared.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Roda depois da validação do JWT: copia o claim "tid" para o TenantContext, que o Hibernate usa
 * para filtrar todas as entidades de negócio. Requisição autenticada sem tenant é recusada (403):
 * sem isso ela enxergaria os dados como "plataforma", sem filtro.
 */
public class TenantContextFilter extends OncePerRequestFilter {

  public static final String TENANT_CLAIM = "tid";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {

    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
      chain.doFilter(request, response);
      return;
    }

    UUID tenantId;
    try {
      tenantId = UUID.fromString(jwtAuth.getToken().getClaimAsString(TENANT_CLAIM));
    } catch (RuntimeException e) {
      forbidden(response);
      return;
    }

    TenantContext.set(tenantId);
    // Área do cliente: o token traz o cliente (cid); usado para filtrar as OS dele
    var customerId = jwtAuth.getToken().getClaimAsString("cid");
    if (customerId != null) {
      CustomerContext.set(UUID.fromString(customerId));
    }
    // Logs (inclusive JSON/ECS em produção) passam a trazer luthieria e usuário de cada linha
    MDC.put("tenantId", tenantId.toString());
    var uid = jwtAuth.getToken().getClaimAsString("uid");
    MDC.put("userId", uid != null ? uid : "cliente");
    try {
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      CustomerContext.clear();
      MDC.remove("tenantId");
      MDC.remove("userId");
    }
  }

  private static void forbidden(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("""
        {"type":"about:blank","title":"Forbidden","status":403,\
        "detail":"Token sem luthieria associada."}\
        """);
  }
}
