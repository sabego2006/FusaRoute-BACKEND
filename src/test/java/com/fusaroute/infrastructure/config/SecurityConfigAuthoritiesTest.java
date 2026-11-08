package com.fusaroute.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traduccion de los claims del JWT a authorities (SCRUM-104 y SCRUM-179).
 *
 * Existe porque el test del controlador de administrador NO puede cubrir esto: el
 * {@code jwt()} de Spring Security Test trae su propio conversor y nunca ejecuta el
 * nuestro. Sin este archivo, el claim {@code role} podria dejar de traducirse a
 * {@code ROLE_ADMIN} y la suite seguiria verde mientras el panel real queda abierto o
 * cerrado para todos.
 */
class SecurityConfigAuthoritiesTest {

    private final SecurityConfig config = new SecurityConfig("http://localhost:4200");

    private Collection<GrantedAuthority> authoritiesOf(Map<String, Object> claims) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .issuedAt(Instant.parse("2026-11-07T10:00:00Z"))
                .expiresAt(Instant.parse("2026-11-14T10:00:00Z"))
                .claims(c -> c.putAll(claims))
                .build();
        return config.jwtAuthenticationConverter()
                .convert(jwt)
                .getAuthorities()
                .stream()
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    @Test
    void el_claim_de_rol_se_traduce_a_authority_con_prefijo_role() {
        assertThat(authoritiesOf(Map.of("sub", "1", "scope", "full", "role", "ADMIN")))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_full", "ROLE_ADMIN");
    }

    @Test
    void un_usuario_normal_recibe_role_user() {
        assertThat(authoritiesOf(Map.of("sub", "7", "scope", "full", "role", "USER")))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_full", "ROLE_USER");
    }

    /**
     * Ante la duda, sin privilegios: un token sin {@code role} NO recibe authority de
     * rol. Es deliberadamente distinto del trato que recibe {@code scope} ausente, y
     * la diferencia tiene razon: ahi el valor por defecto abre lo que el usuario ya
     * podia hacer antes de SCRUM-179, y aqui abriria un panel de administrador.
     */
    @Test
    void un_token_sin_claim_de_rol_no_recibe_authority_de_rol() {
        assertThat(authoritiesOf(Map.of("sub", "7", "scope", "full")))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_full");
    }

    @Test
    void un_rol_en_blanco_tampoco_produce_authority() {
        assertThat(authoritiesOf(Map.of("sub", "7", "scope", "full", "role", "   ")))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_full");
    }

    /**
     * Compatibilidad hacia atras de SCRUM-179: un token emitido antes de que
     * existiera el claim {@code scope} se trata como "full" para que los tokens
     * vigentes —que duran una semana, RNF-04— no obliguen a reloguear.
     */
    @Test
    void un_token_sin_scope_se_trata_como_full() {
        assertThat(authoritiesOf(Map.of("sub", "1", "role", "ADMIN")))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_full", "ROLE_ADMIN");
    }

    @Test
    void el_token_de_enrolamiento_recibe_su_propio_scope() {
        assertThat(authoritiesOf(Map.of("sub", "1", "scope", "totp_enroll", "role", "USER")))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_totp_enroll", "ROLE_USER");
    }
}
