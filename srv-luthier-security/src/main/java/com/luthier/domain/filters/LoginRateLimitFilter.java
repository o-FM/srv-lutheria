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
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginRateLimitFilter extends OncePerRequestFilter {

  private final int capacity;
  private final Duration refillPeriod;

  private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
      .expireAfterAccess(Duration.ofMinutes(15))
      .maximumSize(100_000)
      .build();

  public LoginRateLimitFilter(int capacity, Duration refillMinutes) {
    this.capacity = capacity;
    this.refillPeriod = refillMinutes;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !(HttpMethod.POST.matches(request.getMethod())
        && "/auth/login".equals(request.getRequestURI()));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    Bucket bucket = buckets.get(request.getRemoteAddr(), k -> newBucket());
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

    if (probe.isConsumed()) {
      filterChain.doFilter(request, response);
      return;
    }

    long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getWriter().write("""
            {"type":"about:blank","title":"Too Many Requests","status":429,\
            "detail":"Muitas tentativas de login. Tente novamente em %d segundos."}
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
