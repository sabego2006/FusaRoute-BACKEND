package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.exception.EmailAlreadyRegisteredException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.out.SecretCipherPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de integracion del adaptador de persistencia contra un Postgres real. Es
 * el unico IT del registro, porque es el unico punto donde el mapeo JPA, Flyway y
 * el UNIQUE de la base solo se pueden verificar de verdad con un motor real.
 *
 * Con {@code disabledWithoutDocker = true} el IT se salta solo si no hay Docker,
 * asi el build no se rompe en una maquina sin el daemon.
 *
 * Desde SCRUM-179 el adaptador exige un {@link SecretCipherPort}. Las pruebas
 * de este IT trabajan con usuarios sin TOTP, asi que basta con un cipher
 * identidad para que la cadena arranque; el cifrado real tiene su propio test
 * unitario en {@code out/security}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserPersistenceAdapter.class, UserPersistenceAdapterIT.TestConfig.class})
@Testcontainers(disabledWithoutDocker = true)
class UserPersistenceAdapterIT {

    @TestConfiguration
    static class TestConfig {
        @Bean
        SecretCipherPort secretCipher() {
            return new SecretCipherPort() {
                @Override
                public String encrypt(String plaintext) {
                    return plaintext;
                }

                @Override
                public String decrypt(String ciphertext) {
                    return ciphertext;
                }
            };
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Flyway aplica V1..V4 sobre el contenedor y Hibernate valida el mapeo.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private UserPersistenceAdapter adapter;

    @Test
    void save_and_re_read_user() {
        User saved = adapter.save(User.register("Ana", Email.of("ana@x.co"), "$2a$12$hash"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail().value()).isEqualTo("ana@x.co");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void exists_by_email_finds_saved_user() {
        adapter.save(User.register("Beto", Email.of("beto@x.co"), "$2a$12$hash"));

        assertThat(adapter.existsByEmail(Email.of("beto@x.co"))).isTrue();
        assertThat(adapter.existsByEmail(Email.of("nadie@x.co"))).isFalse();
    }

    @Test
    void find_by_email_returns_user_with_hash_or_empty() {
        adapter.save(User.register("Dora", Email.of("dora@x.co"), "$2a$12$hashdora"));

        assertThat(adapter.findByEmail(Email.of("dora@x.co")))
                .hasValueSatisfying(u -> {
                    assertThat(u.getName()).isEqualTo("Dora");
                    assertThat(u.getPasswordHash()).isEqualTo("$2a$12$hashdora");
                    assertThat(u.isActive()).isTrue();
                });
        assertThat(adapter.findByEmail(Email.of("nadie@x.co"))).isEmpty();
    }

    @Test
    void unique_constraint_translates_to_email_already_registered() {
        adapter.save(User.register("Cira", Email.of("cira@x.co"), "$2a$12$hash"));

        assertThatThrownBy(() -> adapter.save(User.register("Otra", Email.of("cira@x.co"), "$2a$12$hash")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }
}

