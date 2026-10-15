package com.luthier.services.impl;

import com.luthier.domain.entities.UserEntity;
import com.luthier.services.TokenService;
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

  private static final String ISSUER = "luthieria";

  private final JwtEncoder jwtEncoder;
  private final long expirationInMinutes;

  public TokenServiceImpl(
      JwtEncoder jwtEncoder,
      @Value("${luthieria.jwt.expiracao-minutos}") long expirationInMinutes) {
    this.jwtEncoder = jwtEncoder;
    this.expirationInMinutes = expirationInMinutes;
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

  @Override
  public long expirationInSeconds() {
    return expirationInMinutes * 60;
  }
}
