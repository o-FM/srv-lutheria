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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limit por IP (token bucket) para rotas públicas sensíveis a força bruta/enumeração:
 * login e recuperação de senha, cadastro de luthieria e portal do cliente (token na URL).
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Set<String> AUTH_PATHS = Set.of(
      "/auth/login", "/auth/refresh", "/auth/forgot-password", "/auth/reset-password",
      "/auth/register");

  private final Predicate<HttpServletRequest> matcher;
  private final Function<HttpServletRequest, String> bucketKey;
  private final int capacity;
  private final Duration refillPeriod;

  private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
      .expireAfterAccess(Duration.ofMinutes(15))
      .maximumSize(100_000)
      .build();

  private RateLimitFilter(Predicate<HttpServletRequest> matcher,
      Function<HttpServletRequest, String> bucketKey, int capacity, Duration refillPeriod) {
    this.matcher = matcher;
    this.bucketKey = bucketKey;
    this.capacity = capacity;
    this.refillPeriod = refillPeriod;
  }

  /** POST nas rotas de /auth: um balde por rota + IP. */
  public static RateLimitFilter auth(int capacity, Duration refillPeriod) {
    return new RateLimitFilter(
        r -> HttpMethod.POST.matches(r.getMethod()) && AUTH_PATHS.contains(r.getRequestURI()),
        r -> r.getRequestURI() + "|" + r.getRemoteAddr(),
        capacity, refillPeriod);
  }

  /** Portal do cliente: um balde por IP para todas as rotas /portal (dificulta varrer tokens). */
  public static RateLimitFilter portal(int capacity, Duration refillPeriod) {
    return new RateLimitFilter(
        r -> r.getRequestURI().startsWith("/portal/"),
        r -> "portal|" + r.getRemoteAddr(),
        capacity, refillPeriod);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !matcher.test(request);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {

    // Com server.forward-headers-strategy=native, getRemoteAddr() traz o IP real do cliente
    ConsumptionProbe probe = buckets.get(bucketKey.apply(request), k -> newBucket())
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

  private Bucket newBucket() {
    return Bucket.builder()
        .addLimit(Bandwidth.builder()
            .capacity(capacity)
            .refillIntervally(capacity, refillPeriod)
            .build())
        .build();
  }
}
