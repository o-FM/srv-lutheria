package com.luthier.security.domain.filters;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limit por IP (token bucket) para rotas públicas sensíveis a força bruta/enumeração:
 * login e recuperação de senha, cadastro de luthieria e portal do cliente (token na URL).
 *
 * As rotas são casadas com {@link PathPatternRequestMatcher}, o mesmo mecanismo do
 * authorizeHttpRequests e do roteamento do Spring MVC, que trabalha sobre o path decodificado.
 * Comparar getRequestURI() (cru) deixava /auth/%6cogin passar sem limite (SEC-01). Pelo mesmo
 * motivo, a chave do balde é a rota canônica, nunca a grafia recebida.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final List<String> AUTH_PATHS = List.of(
      "/auth/login", "/auth/refresh", "/auth/forgot-password", "/auth/reset-password",
      "/auth/register");

  private static final String PORTAL_ROUTE = "portal";

  /** "auth" ou "portal": cada instância tem seu próprio cache de baldes e marcador de execução. */
  private final String scope;
  private final List<LimitedRoute> routes;
  private final int capacity;
  private final Duration refillPeriod;

  private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
      .expireAfterAccess(Duration.ofMinutes(15))
      .maximumSize(100_000)
      .build();

  /** Rota limitada: o nome canônico identifica o balde, o matcher decide se a requisição é dela. */
  private record LimitedRoute(String name, RequestMatcher matcher) {
  }

  private RateLimitFilter(String scope, List<LimitedRoute> routes, int capacity,
      Duration refillPeriod) {
    this.scope = scope;
    this.routes = List.copyOf(routes);
    this.capacity = capacity;
    this.refillPeriod = refillPeriod;
  }

  /** POST nas rotas de /auth: um balde por rota + IP. */
  public static RateLimitFilter auth(int capacity, Duration refillPeriod) {
    var paths = PathPatternRequestMatcher.withDefaults();
    var routes = AUTH_PATHS.stream()
        .map(path -> new LimitedRoute(path, paths.matcher(HttpMethod.POST, path)))
        .toList();
    return new RateLimitFilter("auth", routes, capacity, refillPeriod);
  }

  /** Portal do cliente: um balde por IP para todas as rotas /portal (dificulta varrer tokens). */
  public static RateLimitFilter portal(int capacity, Duration refillPeriod) {
    var route = new LimitedRoute(PORTAL_ROUTE,
        PathPatternRequestMatcher.withDefaults().matcher("/portal/**"));
    return new RateLimitFilter(PORTAL_ROUTE, List.of(route), capacity, refillPeriod);
  }

  /**
   * As duas instâncias (auth e portal) ficam na mesma cadeia. O nome padrão do marcador do
   * OncePerRequestFilter deriva da classe, então seria compartilhado e a segunda instância seria
   * pulada em silêncio sempre que a primeira já tivesse rodado.
   */
  @Override
  protected String getAlreadyFilteredAttributeName() {
    return RateLimitFilter.class.getName() + "." + scope + ALREADY_FILTERED_SUFFIX;
  }

  /** Requisições fora das rotas limitadas nem entram no filtro (e não marcam execução). */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return matchingRoute(request).isEmpty();
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {

    // shouldNotFilter já garantiu que a requisição casa com uma das rotas
    var route = matchingRoute(request).orElseThrow();

    // Com server.forward-headers-strategy=native, getRemoteAddr() traz o IP real do cliente
    var key = route.name() + "|" + request.getRemoteAddr();
    ConsumptionProbe probe = buckets.get(key, k -> newBucket())
        .tryConsumeAndReturnRemaining(1);

    if (probe.isConsumed()) {
      chain.doFilter(request, response);
      return;
    }

    long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,\
                "detail":"Muitas tentativas. Tente novamente em %d segundos."}\
                """.formatted(retryAfter));
  }

  private Optional<LimitedRoute> matchingRoute(HttpServletRequest request) {
    return routes.stream().filter(route -> route.matcher().matches(request)).findFirst();
  }

  private Bucket newBucket() {
    return Bucket.builder()
        .addLimit(Bandwidth.builder()
            .capacity(capacity)
            .refillIntervally(capacity, refillPeriod)
            .build())
        .build();
  }
}
