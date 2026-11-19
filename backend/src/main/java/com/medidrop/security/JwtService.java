package com.medidrop.security;

import com.medidrop.config.MedidropProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final MedidropProperties properties;
    private SecretKey key;

    @PostConstruct
    void init() {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "MEDIDROP_JWT_SECRET must be configured with at least 32 bytes of entropy");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(Long userId, String email, Role role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getJwt().getAccessTokenMinutes() * 60);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(properties.getJwt().getIssuer())
                .claim("email", email)
                .claim("role", role.name())
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getJwt().getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long accessTokenSeconds() {
        return properties.getJwt().getAccessTokenMinutes() * 60;
    }
}
