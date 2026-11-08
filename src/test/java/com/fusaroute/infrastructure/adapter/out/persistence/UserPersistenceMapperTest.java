package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traduccion entre el usuario de dominio y su entidad JPA, en los dos sentidos.
 *
 * Lo que este test protege es un fallo silencioso: si el mapper deja de llevar un
 * campo, la aplicacion compila, arranca y responde, pero ese dato se pierde en cada
 * guardado. El caso concreto que lo motivo es el registro de ingresos (SCRUM-101) —
 * si {@code last_login_at} no cruza la frontera, el contador de respuestas nuevas de
 * RF-14 queda en cero para siempre sin que nada falle.
 *
 * El secreto TOTP entra y sale como parametro explicito porque cruza esta frontera
 * cifrado: el cifrado vive en el adaptador, no en el mapper.
 */
class UserPersistenceMapperTest {

    private static final Instant ULTIMO = Instant.parse("2026-11-07T08:00:00Z");
    private static final Instant ANTERIOR = Instant.parse("2026-11-01T08:00:00Z");
    private static final Instant BLOQUEADO_HASTA = Instant.parse("2026-11-07T09:00:00Z");

    private static User usuarioCompleto() {
        return new User(7L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", "3101234567",
                UserRole.ADMIN, true, 2, true, "SECRETOENCLARO", BLOQUEADO_HASTA,
                ULTIMO, ANTERIOR);
    }

    @Test
    void a_la_entidad_lleva_todos_los_campos_y_el_secreto_ya_cifrado() {
        UserJpaEntity entidad = UserPersistenceMapper.toEntity(usuarioCompleto(), "CIPHERTEXT");

        assertThat(entidad.getId()).isEqualTo(7L);
        assertThat(entidad.getName()).isEqualTo("Ana");
        assertThat(entidad.getEmail()).isEqualTo("ana@x.co");
        assertThat(entidad.getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(entidad.getPhone()).isEqualTo("3101234567");
        assertThat(entidad.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(entidad.isActive()).isTrue();
        assertThat(entidad.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(entidad.isTotpEnabled()).isTrue();
        // El dominio tiene el secreto en claro; a la columna va el ciphertext que
        // pasa el adaptador, nunca el valor del objeto de dominio.
        assertThat(entidad.getTotpSecret()).isEqualTo("CIPHERTEXT");
        assertThat(entidad.getLockedUntil()).isEqualTo(BLOQUEADO_HASTA);
    }

    /** SCRUM-101: las dos columnas de ingreso tienen que cruzar hacia la entidad. */
    @Test
    void a_la_entidad_lleva_los_dos_instantes_de_ingreso() {
        UserJpaEntity entidad = UserPersistenceMapper.toEntity(usuarioCompleto(), "CIPHERTEXT");

        assertThat(entidad.getLastLoginAt()).isEqualTo(ULTIMO);
        assertThat(entidad.getPreviousLoginAt()).isEqualTo(ANTERIOR);
    }

    @Test
    void al_dominio_devuelve_todos_los_campos_y_el_secreto_ya_descifrado() {
        UserJpaEntity entidad = new UserJpaEntity(7L, "Ana", "ana@x.co", "$2a$12$hash",
                "3101234567", UserRole.ADMIN, true, 2, true, "CIPHERTEXT",
                BLOQUEADO_HASTA, ULTIMO, ANTERIOR);

        User usuario = UserPersistenceMapper.toDomain(entidad, "SECRETOENCLARO");

        assertThat(usuario.getId()).isEqualTo(7L);
        assertThat(usuario.getName()).isEqualTo("Ana");
        assertThat(usuario.getEmail()).isEqualTo(Email.of("ana@x.co"));
        assertThat(usuario.getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(usuario.getPhone()).isEqualTo("3101234567");
        assertThat(usuario.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(usuario.isActive()).isTrue();
        assertThat(usuario.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(usuario.isTotpEnabled()).isTrue();
        assertThat(usuario.getTotpSecret()).isEqualTo("SECRETOENCLARO");
        assertThat(usuario.getLockedUntil()).isEqualTo(BLOQUEADO_HASTA);
    }

    /** SCRUM-101: y tienen que cruzar tambien al leer. */
    @Test
    void al_dominio_devuelve_los_dos_instantes_de_ingreso() {
        UserJpaEntity entidad = new UserJpaEntity(7L, "Ana", "ana@x.co", "$2a$12$hash",
                null, UserRole.USER, true, 0, false, null, null, ULTIMO, ANTERIOR);

        User usuario = UserPersistenceMapper.toDomain(entidad, null);

        assertThat(usuario.getLastLoginAt()).isEqualTo(ULTIMO);
        assertThat(usuario.getPreviousLoginAt()).isEqualTo(ANTERIOR);
    }

    /**
     * Un usuario recien registrado no ha entrado nunca: las dos columnas van en NULL y
     * eso significa "no hay un antes". Es de lo que depende que el contador de
     * respuestas nuevas devuelva 0 en el primer ingreso en vez de contar todo.
     */
    @Test
    void un_usuario_que_nunca_inicio_sesion_cruza_con_los_ingresos_en_nulo() {
        User nuevo = User.register("Ana", Email.of("ana@x.co"), "$2a$12$hash");

        UserJpaEntity entidad = UserPersistenceMapper.toEntity(nuevo, null);

        assertThat(entidad.getLastLoginAt()).isNull();
        assertThat(entidad.getPreviousLoginAt()).isNull();
        assertThat(UserPersistenceMapper.toDomain(entidad, null).getLastLoginAt()).isNull();
    }

    /**
     * Ida y vuelta completa: lo que entra tiene que salir igual. Es la forma mas corta
     * de notar que se perdio un campo en el camino.
     */
    @Test
    void ida_y_vuelta_conserva_el_usuario() {
        User original = usuarioCompleto();

        User despues = UserPersistenceMapper.toDomain(
                UserPersistenceMapper.toEntity(original, "CIPHERTEXT"), "SECRETOENCLARO");

        assertThat(despues.getId()).isEqualTo(original.getId());
        assertThat(despues.getName()).isEqualTo(original.getName());
        assertThat(despues.getEmail()).isEqualTo(original.getEmail());
        assertThat(despues.getRole()).isEqualTo(original.getRole());
        assertThat(despues.getFailedLoginAttempts()).isEqualTo(original.getFailedLoginAttempts());
        assertThat(despues.getLockedUntil()).isEqualTo(original.getLockedUntil());
        assertThat(despues.getLastLoginAt()).isEqualTo(original.getLastLoginAt());
        assertThat(despues.getPreviousLoginAt()).isEqualTo(original.getPreviousLoginAt());
    }
}
