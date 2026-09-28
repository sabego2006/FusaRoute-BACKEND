package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void register_crea_usuario_activo_con_rol_user() {
        User user = User.register("Ana", Email.of("ana@x.co"), "$2a$12$hash");

        assertThat(user.getId()).isNull();
        assertThat(user.getName()).isEqualTo("Ana");
        assertThat(user.getEmail().value()).isEqualTo("ana@x.co");
        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(user.getPhone()).isNull();
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void updateProfile_devuelve_copia_con_datos_nuevos() {
        User original = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash",
                null, UserRole.USER, true);

        User updated = original.updateProfile("Ana Maria", Email.of("nueva@x.co"), "3101234567");

        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getName()).isEqualTo("Ana Maria");
        assertThat(updated.getEmail().value()).isEqualTo("nueva@x.co");
        assertThat(updated.getPhone()).isEqualTo("3101234567");
        assertThat(updated.getPasswordHash()).isEqualTo("$2a$12$hash");
        // El original no se muto
        assertThat(original.getName()).isEqualTo("Ana");
        assertThat(original.getPhone()).isNull();
    }

    @Test
    void withPasswordHash_devuelve_copia_con_nuevo_hash() {
        User original = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$viejo",
                "3101234567", UserRole.USER, true);

        User updated = original.withPasswordHash("$2a$12$nuevo");

        assertThat(updated.getPasswordHash()).isEqualTo("$2a$12$nuevo");
        assertThat(updated.getName()).isEqualTo("Ana");
        assertThat(updated.getPhone()).isEqualTo("3101234567");
        assertThat(original.getPasswordHash()).isEqualTo("$2a$12$viejo");
    }
}
