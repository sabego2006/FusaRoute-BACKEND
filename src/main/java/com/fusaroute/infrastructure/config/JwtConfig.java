package com.fusaroute.infrastructure.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Firma y validacion de JWT con HS256 (clave simetrica: este backend es a la vez
 * quien emite y quien valida, asi que no hace falta un par de llaves).
 *
 * {@code app.jwt.secret} es Base64 y NO tiene valor por defecto en ningun perfil:
 * si falta, o si decodifica a menos de 32 bytes (256 bits, el minimo de HS256), el
 * arranque falla. Un secreto debil que arranca "en silencio" es peor que un error.
 */
@Configuration
public class JwtConfig {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;

    public JwtConfig(@Value("${app.jwt.secret}") String base64Secret) {
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(base64Secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) no es Base64 valido", e);
        }
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret (JWT_SECRET) debe decodificar a al menos " + MIN_SECRET_BYTES
                            + " bytes; genera uno con: openssl rand -base64 32");
        }
        this.key = new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
