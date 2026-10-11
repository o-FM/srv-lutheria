package com.luthier.domain.records;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, RateLimit rateLimit) {
  public record Cors(List<String> allowedOrigins) {}
  public record RateLimit(Login login) {
    public record Login(int capacity, int refillMinutes) {}
  }
}
