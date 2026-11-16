package com.maverickignite.holmes.auth;

import com.maverickignite.holmes.config.HolmesProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

  public static final String ISSUER = "holms-nexus";

  private final JwtEncoder encoder;
  private final HolmesProperties properties;

  public record IssuedToken(String token, Instant expiresAt) {}

  public IssuedToken issue(String username) {
    Instant now = Instant.now();
    Instant expires = now.plus(properties.getSecurity().getTokenTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(username)
            .issuedAt(now)
            .expiresAt(expires)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new IssuedToken(token, expires);
  }
}
