package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.FavoriteDestination;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integracion de {@link FavoriteDestinationPersistenceAdapter} contra un
 * Postgres real (RF-10, SCRUM-21). Verifica lo que depende de la base y no del codigo:
 *
 * <ul>
 *   <li>que la migracion V18 aplique y el mapeo JPA valide con {@code ddl-auto=validate};</li>
 *   <li>que {@code user_id} sea la llave primaria, que es como la base garantiza
 *       "un solo favorito activo a la vez";</li>
 *   <li>que marcar un favorito nuevo REEMPLACE al anterior en vez de insertar otra fila;</li>
 *   <li>que {@code ON DELETE CASCADE} se lleve el favorito con el usuario.</li>
 * </ul>
 *
 * {@code disabledWithoutDocker = true}: se salta solo si no hay Docker, sin romper el
 * build en una maquina sin el daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FavoriteDestinationPersistenceAdapter.class)
@Testcontainers(disabledWithoutDocker = true)
class FavoriteDestinationPersistenceAdapterIT {

    private static final Instant MARCADO = Instant.parse("2026-11-04T09:15:00Z");

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
    private FavoriteDestinationPersistenceAdapter adapter;

    @PersistenceContext
    private EntityManager entityManager;

    private Long userId;

    /**
     * {@code favorite_destination.user_id} tiene FK contra {@code users}, asi que sin
     * fila duena no se puede insertar nada. Se inserta con SQL nativo y no con
     * UserPersistenceAdapter para no arrastrar aqui las dependencias del registro
     * (hasheo, cifrado del secreto TOTP), ajenas a lo que este test verifica.
     */
    @BeforeEach
    void creaElUsuarioDuenoDelFavorito() {
        userId = ((Number) entityManager.createNativeQuery("""
                INSERT INTO users (name, email, password_hash, role, active)
                VALUES ('Ana', 'ana.favorito@x.co', '$2a$12$hash', 'USER', TRUE)
                RETURNING id
                """).getSingleResult()).longValue();
    }

    /**
     * Dentro de un {@code @DataJpaTest} todo comparte una transaccion, mientras que en
     * produccion cada metodo del adaptador lleva su propio {@code @Transactional}. Hay
     * que bajar a mano lo que este en el contexto de persistencia para que la siguiente
     * consulta lea de la base y no de memoria.
     */
    private void sincronizaConLaBase() {
        entityManager.flush();
        entityManager.clear();
    }

    private long filasDelUsuario() {
        return ((Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM favorite_destination WHERE user_id = :id")
                .setParameter("id", userId).getSingleResult()).longValue();
    }

    @Test
    void la_llave_primaria_de_la_tabla_es_el_usuario() {
        // Es la traduccion literal de "solo uno activo a la vez": lo garantiza la base,
        // no la confianza en que la aplicacion no inserte el segundo.
        Object columna = entityManager.createNativeQuery("""
                SELECT kcu.column_name
                  FROM information_schema.table_constraints tc
                  JOIN information_schema.key_column_usage kcu
                    ON kcu.constraint_name = tc.constraint_name
                 WHERE tc.table_name = 'favorite_destination'
                   AND tc.constraint_type = 'PRIMARY KEY'
                """).getSingleResult();

        assertThat(columna).isEqualTo("user_id");
    }

    @Test
    void guarda_un_favorito_y_lo_relee() {
        adapter.save(new FavoriteDestination(userId, "Maiz Amarillo", MARCADO));
        sincronizaConLaBase();

        assertThat(adapter.findByUser(userId)).hasValueSatisfying(favorito -> {
            assertThat(favorito.destination()).isEqualTo("Maiz Amarillo");
            assertThat(favorito.savedAt()).isEqualTo(MARCADO);
        });
    }

    @Test
    void marcar_un_favorito_nuevo_reemplaza_al_anterior_sin_dejar_dos_filas() {
        // El criterio de aceptacion de RF-10, verificado contra la base real.
        adapter.save(new FavoriteDestination(userId, "Centro", MARCADO));
        sincronizaConLaBase();

        adapter.save(new FavoriteDestination(userId, "San Antonio", MARCADO.plusSeconds(60)));
        sincronizaConLaBase();

        assertThat(filasDelUsuario()).isEqualTo(1);
        assertThat(adapter.findByUser(userId)).hasValueSatisfying(favorito -> {
            assertThat(favorito.destination()).isEqualTo("San Antonio");
            assertThat(favorito.savedAt()).isEqualTo(MARCADO.plusSeconds(60));
        });
    }

    @Test
    void un_usuario_sin_favorito_devuelve_vacio() {
        assertThat(adapter.findByUser(userId)).isEmpty();
    }

    @Test
    void el_favorito_de_un_usuario_no_es_el_de_otro() {
        Long otro = ((Number) entityManager.createNativeQuery("""
                INSERT INTO users (name, email, password_hash, role, active)
                VALUES ('Beto', 'beto.favorito@x.co', '$2a$12$hash', 'USER', TRUE)
                RETURNING id
                """).getSingleResult()).longValue();

        adapter.save(new FavoriteDestination(userId, "Centro", MARCADO));
        adapter.save(new FavoriteDestination(otro, "Pekin", MARCADO));
        sincronizaConLaBase();

        assertThat(adapter.findByUser(userId)).hasValueSatisfying(
                favorito -> assertThat(favorito.destination()).isEqualTo("Centro"));
        assertThat(adapter.findByUser(otro)).hasValueSatisfying(
                favorito -> assertThat(favorito.destination()).isEqualTo("Pekin"));
    }

    @Test
    void quitar_el_favorito_borra_la_fila() {
        adapter.save(new FavoriteDestination(userId, "Centro", MARCADO));
        sincronizaConLaBase();

        adapter.deleteByUser(userId);
        sincronizaConLaBase();

        assertThat(filasDelUsuario()).isZero();
        assertThat(adapter.findByUser(userId)).isEmpty();
    }

    @Test
    void quitar_un_favorito_que_no_existe_no_falla() {
        // El puerto promete ser idempotente: el usuario pidio quedarse sin favorito y
        // ese resultado ya se cumple.
        adapter.deleteByUser(userId);
        sincronizaConLaBase();

        assertThat(filasDelUsuario()).isZero();
    }

    @Test
    void borrar_el_usuario_se_lleva_su_favorito() {
        // ON DELETE CASCADE de la V18: el favorito es una preferencia del usuario, no
        // un registro que deba sobrevivirle.
        adapter.save(new FavoriteDestination(userId, "Centro", MARCADO));
        sincronizaConLaBase();

        entityManager.createNativeQuery("DELETE FROM users WHERE id = :id")
                .setParameter("id", userId).executeUpdate();
        sincronizaConLaBase();

        assertThat(filasDelUsuario()).isZero();
    }
}
