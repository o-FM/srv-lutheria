package com.luthier.domain.filters;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginRateLimitFilter extends OncePerRequestFilter {

  private static final Set<String> PATHS = Set.of(
      "/auth/login", "/auth/refresh", "/auth/forgot-password", "/auth/reset-password",
      "/auth/register");

  private final int capacity;
  private final Duration refillPeriod;

  private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
      .expireAfterAccess(Duration.ofMinutes(15))
      .maximumSize(100_000)
      .build();

  public LoginRateLimitFilter(int capacity, Duration refillPeriod) {
    this.capacity = capacity;
    this.refillPeriod = refillPeriod;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !(HttpMethod.POST.matches(request.getMethod())
        && PATHS.contains(request.getRequestURI()));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {

    // Com server.forward-headers-strategy=native, getRemoteAddr() traz o IP real do cliente
    String key = request.getRequestURI() + "|" + request.getRemoteAddr();
    ConsumptionProbe probe = buckets.get(key, k -> newBucket()).tryConsumeAndReturnRemaining(1);

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
