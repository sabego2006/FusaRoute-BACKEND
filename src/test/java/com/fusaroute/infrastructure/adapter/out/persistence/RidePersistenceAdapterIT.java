package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Ride;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RidePersistenceAdapter} contra un Postgres real: que la V25 aplique y valide
 * con el mapeo JPA, que la lectura traiga el letrero de la ruta y, sobre todo, que el
 * viaje numero 11 desplace al mas viejo dentro de la misma operacion.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RidePersistenceAdapter.class)
@Testcontainers(disabledWithoutDocker = true)
class RidePersistenceAdapterIT {

    private static final Instant AHORA = Instant.parse("2026-11-09T22:42:00Z");
    private static final Coordinate TERMINAL = new Coordinate(4.3459, -74.3779);

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
    private RidePersistenceAdapter adapter;

    @PersistenceContext
    private EntityManager entityManager;

    private Long userId;
    private Long routeId;
    private String routeName;
    private String signFile;

    @BeforeEach
    void creaUsuarioYTomaUnaRutaDeLaSemilla() {
        userId = ((Number) entityManager.createNativeQuery("""
                INSERT INTO users (name, email, password_hash, role, active)
                VALUES ('Ana', 'ana.viajes@x.co', '$2a$12$hash', 'USER', TRUE)
                RETURNING id
                """).getSingleResult()).longValue();
        Object[] ruta = (Object[]) entityManager.createNativeQuery("""
                SELECT id, name, sign_file FROM routes WHERE sign_file IS NOT NULL ORDER BY id LIMIT 1
                """).getSingleResult();
        routeId = ((Number) ruta[0]).longValue();
        routeName = (String) ruta[1];
        signFile = (String) ruta[2];
    }

    private Ride sube(String destino, Long placeId, Instant cuando) {
        Ride r = adapter.saveKeepingLatest(new Ride(null, userId, routeId, routeName, signFile,
                destino, placeId, TERMINAL, "Indio", cuando), Ride.MAX_PER_USER);
        entityManager.clear();
        return r;
    }

    @Test
    void el_indice_existe() {
        Object nombre = entityManager.createNativeQuery("""
                SELECT indexname FROM pg_indexes
                 WHERE tablename = 'ride_history' AND indexname = 'ix_ride_history_user_boarded'
                """).getSingleResult();
        assertThat(nombre).isEqualTo("ix_ride_history_user_boarded");
    }

    @Test
    void guarda_y_relee_con_el_letrero_de_la_ruta() {
        Ride guardado = sube("Terminal de Transporte", 31L, AHORA);
        assertThat(guardado.id()).isNotNull();

        assertThat(adapter.findRecentByUser(userId, Ride.MAX_PER_USER)).singleElement().satisfies(r -> {
            assertThat(r.routeId()).isEqualTo(routeId);
            assertThat(r.signFile()).isEqualTo(signFile);
            assertThat(r.routeName()).isEqualTo(routeName);
            assertThat(r.destinationPlaceId()).isEqualTo(31L);
            assertThat(r.destination()).isEqualTo(TERMINAL);
            assertThat(r.boardedAt()).isEqualTo(AHORA);
        });
    }

    @Test
    void un_punto_del_mapa_se_guarda_sin_place_id() {
        sube("Punto en el mapa", null, AHORA);
        assertThat(adapter.findRecentByUser(userId, 10).getFirst().destinationPlaceId()).isNull();
    }

    @Test
    void el_viaje_once_desplaza_al_mas_viejo() {
        for (int i = 0; i < Ride.MAX_PER_USER; i++) {
            sube("Destino " + i, null, AHORA.minusSeconds(1000L - i * 60L));
        }
        assertThat(adapter.findRecentByUser(userId, 50)).hasSize(10);

        sube("Destino nuevo", null, AHORA);

        List<Ride> historial = adapter.findRecentByUser(userId, 50);
        assertThat(historial).hasSize(Ride.MAX_PER_USER);
        assertThat(historial.getFirst().destinationLabel()).isEqualTo("Destino nuevo");
        assertThat(historial).extracting(Ride::destinationLabel).doesNotContain("Destino 0");
        Number filas = (Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM ride_history WHERE user_id = :id")
                .setParameter("id", userId).getSingleResult();
        assertThat(filas.longValue()).isEqualTo(10);
    }

    @Test
    void empate_de_instante_se_desempata_por_id() {
        for (int i = 0; i <= Ride.MAX_PER_USER; i++) {
            sube("Destino " + i, null, AHORA);
        }
        List<Ride> historial = adapter.findRecentByUser(userId, 50);
        assertThat(historial).hasSize(10);
        assertThat(historial.getFirst().destinationLabel()).isEqualTo("Destino 10");
        assertThat(historial).extracting(Ride::destinationLabel).doesNotContain("Destino 0");
    }

    @Test
    void los_viajes_de_otro_usuario_no_se_mezclan_ni_se_recortan() {
        Long otro = ((Number) entityManager.createNativeQuery("""
                INSERT INTO users (name, email, password_hash, role, active)
                VALUES ('Beto', 'beto.viajes@x.co', '$2a$12$hash', 'USER', TRUE)
                RETURNING id
                """).getSingleResult()).longValue();
        adapter.saveKeepingLatest(new Ride(null, otro, routeId, routeName, signFile,
                "De Beto", null, TERMINAL, "Indio", AHORA.minusSeconds(99_999)), Ride.MAX_PER_USER);
        for (int i = 0; i <= Ride.MAX_PER_USER; i++) {
            sube("Destino " + i, null, AHORA.plusSeconds(i));
        }

        assertThat(adapter.findRecentByUser(otro, 10)).extracting(Ride::destinationLabel).containsExactly("De Beto");
        assertThat(adapter.findRecentByUser(userId, 50)).hasSize(10);
    }

    @Test
    void borrar_el_usuario_se_lleva_sus_viajes() {
        sube("Terminal", null, AHORA);
        entityManager.createNativeQuery("DELETE FROM users WHERE id = :id").setParameter("id", userId).executeUpdate();
        Number quedan = (Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM ride_history WHERE user_id = :id").setParameter("id", userId).getSingleResult();
        assertThat(quedan.longValue()).isZero();
    }
}
