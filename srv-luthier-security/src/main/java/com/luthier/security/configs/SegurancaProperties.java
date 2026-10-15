package com.luthier.security.configs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SegurancaProperties(
    @DefaultValue("true") boolean requireHttps,
    @NotNull @Valid Cors cors,
    @DefaultValue @Valid RateLimit rateLimit,
    @DefaultValue @Valid Tokens tokens
) {

  public record Cors(@NotEmpty List<String> allowedOrigins) {}

  public record RateLimit(@DefaultValue @Valid Login login) {}

  public record Login(
      @DefaultValue("5") @Positive int capacity,
      @DefaultValue("1") @Positive int refillMinutes) {}

  public record Tokens(
      @DefaultValue("30") @Positive int refreshDays,
      @DefaultValue("30") @Positive int resetMinutes) {}
}
