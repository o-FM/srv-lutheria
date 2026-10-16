package com.luthier.security.configs;

import com.luthier.security.domain.filters.RateLimitFilter;
import com.luthier.security.domain.filters.TenantContextFilter;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@EnableWebSecurity
@EnableMethodSecurity
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter conv,
      SegurancaProperties props) throws Exception {

    var login = props.rateLimit().login();
    var authRateLimit = RateLimitFilter.auth(login.capacity(),
        Duration.ofMinutes(login.refillMinutes()));
    var portal = props.rateLimit().portal();
    var portalRateLimit = RateLimitFilter.portal(portal.capacity(),
        Duration.ofMinutes(portal.refillMinutes()));

    if (props.requireHttps()) {
      http.redirectToHttps(Customizer.withDefaults());
    }

    return http
        .cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .headers(h -> h
            .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true)
                .maxAgeInSeconds(31_536_000) // 1 ANO
            )
            .contentSecurityPolicy(csp -> csp
                .policyDirectives("default-src 'none'; frame-ancestors 'none'")
            )
            .frameOptions(FrameOptionsConfig::deny)
            .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
            .permissionsPolicyHeader(p -> p
                .policy("geolocation=(), camera=(), microphone=()"))
        )
        .authorizeHttpRequests(authorizeRequests -> authorizeRequests
            .requestMatchers("/auth/**").permitAll()
            // Portal do cliente: acesso pelo token aleatório da OS, sem login
            .requestMatchers("/portal/**").permitAll()
            // Probes e métricas: em produção ficam na porta de management (rede interna)
            .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus")
            .permitAll()
            // Demais endpoints de operação não são da conta de nenhuma luthieria
            .requestMatchers("/actuator/**").denyAll()
            // Contrato OpenAPI: só existe quando springdoc.api-docs.enabled=true (dev)
            .requestMatchers("/v3/api-docs/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth -> oauth
            .jwt(jwt -> jwt.jwtAuthenticationConverter(conv))
        )
        .addFilterBefore(authRateLimit, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(portalRateLimit, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(new TenantContextFilter(), BearerTokenAuthenticationFilter.class)
        .build();
  }

  @Bean
  public AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService,
      PasswordEncoder passwordEncoder) {
    var provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);

    return new ProviderManager(provider);
  }
}
