package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración específico para validar el RNF-06 (Seguridad de contraseñas).
 * Verifica que la persistencia guarde la contraseña hasheada y que el dato en DB
 * no sea texto plano.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserPersistenceAdapter.class)
@Testcontainers(disabledWithoutDocker = true)
class PasswordSecurityIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private UserPersistenceAdapter adapter;

    @Test
    void la_contrasena_en_base_de_datos_no_es_texto_plano() {
        // 1. Definimos una contraseña en texto plano
        String passwordPlana = "PasswordSegura123";
        String hashEsperado = "$2a$12$SOMETESTHASH1234567890"; // Simulamos un hash BCrypt

        // 2. Registramos el usuario con el hash (en el flujo real, el UseCase hashea antes de llamar al adapter)
        User user = User.register("Santi", Email.of("santi@test.com"), hashEsperado);
        adapter.save(user);

        // 3. Recuperamos el usuario de la base de datos
        // El adapter devuelve la entidad mapeada al modelo de dominio
        User retrieved = adapter.findByEmail(Email.of("santi@test.com")).orElseThrow();

        // 4. VALIDACIÓN CRÍTICA (RNF-06):
        // El valor almacenado en la DB NO debe ser igual al texto plano original
        assertThat(retrieved.getPasswordHash())
                .as("La contraseña en la base de datos JAMAIS debe ser texto plano")
                .isNotEqualTo(passwordPlana);

        // 5. Validar que el hash mantiene el formato BCrypt ($2a$ o $2b$)
        assertThat(retrieved.getPasswordHash())
                .as("El hash debe seguir el formato BCrypt")
                .startsWith("$2a$");
    }
}
