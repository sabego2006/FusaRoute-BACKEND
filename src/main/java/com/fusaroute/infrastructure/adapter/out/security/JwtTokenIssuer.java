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
 * claim {@code sub}; el rol, en {@code role}. El {@link Clock} se inyecta para
 * que los tests puedan fijar el "ahora" y comprobar la vigencia exacta.
 *
 * SCRUM-179: cada token lleva ademas un claim {@code scope} que decide a que
 * endpoints alcanza. "full" es el token de 7 dias que abre toda la API;
 * "totp_enroll" es el token corto que SOLO sirve para enrolar y confirmar el
 * segundo factor. SecurityConfig traduce esto a authorities
 * ({@code SCOPE_full} / {@code SCOPE_totp_enroll}) y limita las rutas.
 */
@Component
public class JwtTokenIssuer implements TokenIssuerPort {

    static final String ISSUER = "fusaroute";
    static final String SCOPE_FULL = "full";
    static final String SCOPE_TOTP_ENROLL = "totp_enroll";

    private final JwtEncoder encoder;
    private final Clock clock;

    public JwtTokenIssuer(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(User user, Duration validity) {
        return sign(user, validity, SCOPE_FULL);
    }

    @Override
    public IssuedToken issueEnrollmentToken(User user, Duration validity) {
        return sign(user, validity, SCOPE_TOTP_ENROLL);
    }

    private IssuedToken sign(User user, Duration validity, String scope) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(validity);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .claim("scope", scope)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        String value = encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }
}
