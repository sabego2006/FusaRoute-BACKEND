package com.fusaroute.infrastructure.adapter.out.security;

import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.infrastructure.config.JwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba el emisor con el encoder y el decoder reales (los de JwtConfig), sin
 * levantar Spring. El Clock fijo permite comprobar la vigencia exacta.
 */
class JwtTokenIssuerTest {

    // 32 bytes en Base64: el minimo que exige JwtConfig para HS256.
    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY=";
    private static final String OTHER_SECRET = "enl4d3Z1dHNycXBvbm1sa2ppaGdmZWRjYmE5ODc2NTQ=";
    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private final User user = new User(7L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null, UserRole.USER, true);

    private JwtTokenIssuer issuerAt(Instant now, String secret) {
        JwtConfig config = new JwtConfig(secret);
        return new JwtTokenIssuer(config.jwtEncoder(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private JwtDecoder decoder() {
        return new JwtConfig(SECRET).jwtDecoder();
    }

    @Test
    void el_token_vale_siete_dias_y_lleva_el_id_del_usuario() {
        IssuedToken token = issuerAt(Instant.now(), SECRET).issue(user, Duration.ofDays(7));

        Jwt jwt = decoder().decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("fusaroute");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofDays(7));
        assertThat(token.expiresAt().getEpochSecond()).isEqualTo(jwt.getExpiresAt().getEpochSecond());
    }

    @Test
    void un_token_vencido_es_rechazado_al_validar() {
        // Emitido hace 8 dias con vigencia de 7: venció hace un dia.
        IssuedToken token = issuerAt(Instant.now().minus(Duration.ofDays(8)), SECRET)
                .issue(user, Duration.ofDays(7));

        assertThatThrownBy(() -> decoder().decode(token.value()))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void un_token_firmado_con_otra_clave_es_rechazado() {
        IssuedToken token = issuerAt(NOW, OTHER_SECRET).issue(user, Duration.ofDays(7));

        assertThatThrownBy(() -> decoder().decode(token.value()))
                .isInstanceOf(org.springframework.security.oauth2.jwt.BadJwtException.class);
    }

    @Test
    void el_secreto_corto_o_no_base64_rompe_el_arranque() {
        assertThatThrownBy(() -> new JwtConfig("Y29ydG8="))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new JwtConfig("###no-es-base64###"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }
}
