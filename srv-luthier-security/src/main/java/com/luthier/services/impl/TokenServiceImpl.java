package com.luthier.services.impl;

import com.luthier.services.TokenService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenServiceImpl implements TokenService {

  private final JwtEncoder jwtEncoder;
  private final long expirationInMinutes;

  public TokenServiceImpl(
      JwtEncoder jwtEncoder,
      @Value("${luthieria.jwt.expiracao-minutos}")long expirationInMinutes) {
    this.jwtEncoder = jwtEncoder;
    this.expirationInMinutes = expirationInMinutes;
  }

  @Override
  public String generate(Authentication auth) {
    var instantNow = Instant.now();
    var roles = auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(a -> a.startsWith("ROLE_"))
        .map(r -> r.replace("ROLE_", ""))
        .toList();

    var claims = JwtClaimsSet.builder()
        .issuer("luthieria")
        .subject(auth.getName())
        .issuedAt(instantNow)
        .expiresAt(instantNow.plus(expirationInMinutes, ChronoUnit.MINUTES))
        .claim("roles", roles)
        .build();

    var header = JwsHeader.with(MacAlgorithm.HS256).build();

    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  @Override
  public long expirationInSeconds() {
    return expirationInMinutes * 120;
  }
}
