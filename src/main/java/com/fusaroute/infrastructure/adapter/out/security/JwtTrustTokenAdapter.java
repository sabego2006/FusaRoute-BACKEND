package com.fusaroute.infrastructure.adapter.out.security;

import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.out.TrustTokenPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Adaptador del dispositivo de confianza del segundo factor (decision de
 * producto del 17-nov-2026, SCRUM-196): un JWT HS256 sin estado, firmado con la misma
 * llave que el token de acceso ({@link JwtConfig}) -- no hace falta tabla ni
 * migracion nueva.
 *
 * El claim {@code fp} es un hash SHA-256 (un solo sentido) de la contrasena y
 * el secreto TOTP vigentes del usuario ({@link User#trustFingerprintSource()}),
 * NUNCA los valores en claro: el token viaja al navegador y un JWT firmado no
 * es secreto, solo infalsificable, asi que poner el secreto TOTP real en un
 * claim seria regalarlo. Cambiar la contrasena o el secreto TOTP cambia la
 * huella, y la comparacion en {@link #isValid} deja de coincidir: eso es toda
 * la invalidacion, sin tocar el esquema.
 */
@Component
public class JwtTrustTokenAdapter implements TrustTokenPort {

    static final String SCOPE_TOTP_TRUST = "totp_trust";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;

    public JwtTrustTokenAdapter(JwtEncoder encoder, JwtDecoder decoder, Clock clock) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.clock = clock;
    }

    @Override
    public String issue(User user, Duration validity) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtTokenIssuer.ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim("scope", SCOPE_TOTP_TRUST)
                .claim("fp", fingerprint(user))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(validity))
                .build();
        return encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Override
    public boolean isValid(String token, User user) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException e) {
            // Vencido, con firma ajena o con un formato que no es un JWT: nunca
            // rompe el login, solo significa "no confiar" y pedir el codigo.
            return false;
        }
        return SCOPE_TOTP_TRUST.equals(jwt.getClaimAsString("scope"))
                && String.valueOf(user.getId()).equals(jwt.getSubject())
                && fingerprint(user).equals(jwt.getClaimAsString("fp"));
    }

    private static String fingerprint(User user) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(user.trustFingerprintSource().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 esta en todo JDK compatible con los nombres estandar de la
            // JCA; si falta, el runtime esta roto, no la logica de negocio.
            throw new IllegalStateException(e);
        }
    }
}
