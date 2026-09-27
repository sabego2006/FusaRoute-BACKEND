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
}
