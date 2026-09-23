package com.fusaroute.infrastructure.adapter.out.security;

import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.out.TokenServicePort;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementación real de JWT para el sistema de autenticación.
 * Genera tokens con validez de una semana (RNF-04).
 */
@Component
public class JwtTokenService implements TokenServicePort {

    private final SecretKey secretKey;
    private final long validityMillis;

    public JwtTokenService(
            @Value("${app.jwt.secret:secret-key-de-desarrollo-con-al-menos-32-caracteres-para-seguridad}")
            String secret,
            @Value("${app.jwt.validity-ms:604800000}") // 7 días en ms
            long validityMillis) {

        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMillis = validityMillis;
    }

    @Override
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("name", user.getName());

        return Jwts.builder()
                .claims(claims)
                .subject(user.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + validityMillis))
                .signWith(secretKey)
                .compact();
    }
}
