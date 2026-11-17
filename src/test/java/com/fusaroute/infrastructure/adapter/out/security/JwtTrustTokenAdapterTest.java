package com.fusaroute.infrastructure.adapter.out.security;

import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.infrastructure.config.JwtConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el adaptador con el encoder y el decoder reales (los de JwtConfig),
 * sin levantar Spring. A diferencia de {@link JwtTokenIssuerTest}, aqui nunca
 * se espera una excepcion: {@link JwtTrustTokenAdapter#isValid} absorbe
 * cualquier motivo de rechazo y devuelve {@code false} (ver su javadoc).
 */
class JwtTrustTokenAdapterTest {

    // 32 bytes en Base64: el minimo que exige JwtConfig para HS256.
    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY=";
    private static final String OTHER_SECRET = "enl4d3Z1dHNycXBvbm1sa2ppaGdmZWRjYmE5ODc2NTQ=";
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
    private static final Duration VALIDITY = Duration.ofDays(15);

    private final User user = new User(7L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
            UserRole.USER, true, 0, true, "JBSWY3DPEHPK3PXP", null);
    private final User otherUser = new User(9L, "Beto", Email.of("beto@x.co"), "$2a$12$hash2", null,
            UserRole.USER, true, 0, true, "AAAAAAAAAAAAAAAA", null);

    private JwtTrustTokenAdapter adapterAt(Instant now, String secret) {
        JwtConfig config = new JwtConfig(secret);
        return new JwtTrustTokenAdapter(config.jwtEncoder(), config.jwtDecoder(), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void un_token_recien_emitido_es_valido_para_el_mismo_usuario() {
        JwtTrustTokenAdapter adapter = adapterAt(NOW, SECRET);
        String token = adapter.issue(user, VALIDITY);

        assertThat(adapter.isValid(token, user)).isTrue();
    }

    @Test
    void un_token_vencido_no_es_valido() {
        String token = adapterAt(NOW.minus(VALIDITY).minusSeconds(1), SECRET).issue(user, VALIDITY);
        JwtTrustTokenAdapter hoy = adapterAt(NOW, SECRET);

        assertThat(hoy.isValid(token, user)).isFalse();
    }

    @Test
    void un_token_de_otro_usuario_es_rechazado() {
        JwtTrustTokenAdapter adapter = adapterAt(NOW, SECRET);
        String token = adapter.issue(user, VALIDITY);

        assertThat(adapter.isValid(token, otherUser)).isFalse();
    }

    @Test
    void cambiar_la_contrasena_invalida_el_token_ya_emitido() {
        JwtTrustTokenAdapter adapter = adapterAt(NOW, SECRET);
        String token = adapter.issue(user, VALIDITY);
        User conOtraContrasena = user.withPasswordHash("$2a$12$otroHash");

        assertThat(adapter.isValid(token, conOtraContrasena)).isFalse();
    }

    @Test
    void reenrolar_o_desactivar_el_totp_invalida_el_token_ya_emitido() {
        JwtTrustTokenAdapter adapter = adapterAt(NOW, SECRET);
        String token = adapter.issue(user, VALIDITY);
        User conOtroSecretoTotp = user.resetTotp().startTotpEnrollment("OTROSECRETOTOTP1234");

        assertThat(adapter.isValid(token, conOtroSecretoTotp)).isFalse();
    }

    @Test
    void un_token_firmado_con_otra_clave_es_rechazado() {
        String token = adapterAt(NOW, OTHER_SECRET).issue(user, VALIDITY);
        JwtTrustTokenAdapter real = adapterAt(NOW, SECRET);

        assertThat(real.isValid(token, user)).isFalse();
    }

    @Test
    void un_token_manipulado_vacio_o_nulo_es_rechazado() {
        JwtTrustTokenAdapter adapter = adapterAt(NOW, SECRET);

        assertThat(adapter.isValid("esto-no-es-un-jwt", user)).isFalse();
        assertThat(adapter.isValid("", user)).isFalse();
        assertThat(adapter.isValid(null, user)).isFalse();
    }

    @Test
    void el_token_de_acceso_full_no_sirve_como_token_de_confianza() {
        JwtConfig config = new JwtConfig(SECRET);
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        JwtTokenIssuer accessIssuer = new JwtTokenIssuer(config.jwtEncoder(), fixedClock);
        JwtTrustTokenAdapter adapter = new JwtTrustTokenAdapter(config.jwtEncoder(), config.jwtDecoder(), fixedClock);

        String fullToken = accessIssuer.issue(user, Duration.ofDays(7)).value();

        assertThat(adapter.isValid(fullToken, user)).isFalse();
    }
}
