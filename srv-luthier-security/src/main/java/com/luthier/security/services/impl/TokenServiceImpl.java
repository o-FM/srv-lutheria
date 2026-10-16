package com.luthier.security.services.impl;

import com.luthier.security.domain.entities.UserEntity;
import com.luthier.security.services.TokenService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenServiceImpl implements TokenService {

  public static final String ISSUER = "luthieria";

  private final JwtEncoder jwtEncoder;
  private final long expirationInMinutes;
  private final long customerExpirationInHours;

  public TokenServiceImpl(
      JwtEncoder jwtEncoder,
      @Value("${luthieria.jwt.expiracao-minutos}") long expirationInMinutes,
      @Value("${luthieria.jwt.cliente-horas:168}") long customerExpirationInHours) {
    this.jwtEncoder = jwtEncoder;
    this.expirationInMinutes = expirationInMinutes;
    this.customerExpirationInHours = customerExpirationInHours;
  }

  @Override
  public String generate(UserEntity user) {
    var now = Instant.now();

    var claims = JwtClaimsSet.builder()
        .issuer(ISSUER)
        .subject(user.getEmail())
        .id(UUID.randomUUID().toString())                       // jti
        .issuedAt(now)
        .expiresAt(now.plus(expirationInMinutes, ChronoUnit.MINUTES))
        .claim("uid", user.getUuid().toString())
        // tenant (luthieria) do usuário: o TenantContextFilter usa para isolar os dados
        .claim("tid", user.getTenantId().toString())
        // lido pelo JwtAuthenticationConverter (JwtConfig) e vira ROLE_<valor>
        .claim("roles", List.of(user.getProfile().getValue()))
        .build();

    var header = JwsHeader.with(MacAlgorithm.HS256).build();

    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  /**
   * Sessão longa (padrão 7 dias) e sem refresh: o cliente só lê as próprias OS e responde
   * orçamentos; ao expirar, pede um novo código pelo WhatsApp.
   */
  @Override
  public String generateForCustomer(UUID tenantId, UUID customerId, String phone) {
    var now = Instant.now();
    var claims = JwtClaimsSet.builder()
        .issuer(ISSUER)
        .subject("cliente:" + customerId)
        .id(UUID.randomUUID().toString())
        .issuedAt(now)
        .expiresAt(now.plus(customerExpirationInHours, ChronoUnit.HOURS))
        .claim("tid", tenantId.toString())
        .claim("cid", customerId.toString())
        .claim("roles", List.of(com.luthier.shared.authorization.Roles.CUSTOMER))
        .build();
    var header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  @Override
  public long customerExpirationInSeconds() {
    return customerExpirationInHours * 3600;
  }

  @Override
  public long expirationInSeconds() {
    return expirationInMinutes * 60;
  }
}
