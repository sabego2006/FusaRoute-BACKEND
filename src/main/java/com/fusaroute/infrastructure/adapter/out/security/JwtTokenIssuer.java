package com.fusaroute.infrastructure.adapter.out.security;

import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.out.TokenIssuerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Adaptador que firma el JWT de acceso con HS256. El id del usuario viaja en el
 * claim {@code sub}; el rol, en {@code role}. El {@link Clock} se inyecta para que
 * los tests puedan fijar el "ahora" y comprobar la vigencia exacta.
 */
@Component
public class JwtTokenIssuer implements TokenIssuerPort {

    static final String ISSUER = "fusaroute";

    private final JwtEncoder encoder;
    private final Clock clock;

    public JwtTokenIssuer(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(User user, Duration validity) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(validity);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        String value = encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }
}
