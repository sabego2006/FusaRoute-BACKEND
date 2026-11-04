package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.SearchRecord;
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
 * Test de integracion de {@link SearchHistoryPersistenceAdapter} contra un Postgres
 * real (RF-07, SCRUM-20). Es la unica prueba que verifica de verdad los criterios de
 * aceptacion de la historia, porque los tres dependen de la base y no del codigo:
 *
 * <ul>
 *   <li>que la migracion V17 aplique y el mapeo JPA valide contra la tabla real
 *       (SCRUM-78) — lo comprueba el solo hecho de que el contexto arranque con
 *       {@code ddl-auto=validate};</li>
 *   <li>que el indice {@code ix_search_history_user_created} exista (SCRUM-78);</li>
 *   <li>que al pasar de seis busquedas se borre la mas antigua (SCRUM-80).</li>
 * </ul>
 *
 * Flyway aplica aqui el historial completo, asi que la tabla es la que crea la V17,
 * no una fabricada por Hibernate.
 *
 * {@code disabledWithoutDocker = true}, igual que {@link UserPersistenceAdapterIT}: se
 * salta solo si no hay Docker, sin romper el build en una maquina sin el daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SearchHistoryPersistenceAdapter.class)
@Testcontainers(disabledWithoutDocker = true)
class SearchHistoryPersistenceAdapterIT {

    private static final Instant AHORA = Instant.parse("2026-11-04T09:15:00Z");

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
    private SearchHistoryPersistenceAdapter adapter;

    @PersistenceContext
    private EntityManager entityManager;

    private Long userId;

    /**
     * Un usuario propio por test: {@code search_history.user_id} tiene FK contra
     * {@code users}, asi que sin fila duena no se puede insertar nada. Se inserta con
     * SQL nativo y no con UserPersistenceAdapter para no arrastrar aqui las
     * dependencias del registro (hasheo, cifrado del secreto TOTP), que no tienen
     * nada que ver con lo que este test verifica.
     */
    @BeforeEach
    void creaElUsuarioDuenoDelHistorial() {
        userId = ((Number) entityManager.createNativeQuery("""
                INSERT INTO users (name, email, password_hash, role, active)
                VALUES ('Ana', 'ana.historial@x.co', '$2a$12$hash', 'USER', TRUE)
                RETURNING id
                """).getSingleResult()).longValue();
    }

    /**
     * El adaptador guarda y recorta en transacciones distintas en produccion (cada
     * metodo lleva su propio {@code @Transactional} y el controlador no abre ninguna),
     * asi que el INSERT ya esta en la base cuando corre el DELETE nativo. Dentro de un
     * {@code @DataJpaTest} todo comparte una sola transaccion, de modo que hay que
     * bajar a mano lo que este en el contexto de persistencia para reproducir esa
     * condicion; sin esto el DELETE no veria las filas recien guardadas.
     */
    private void sincronizaConLaBase() {
        entityManager.flush();
        entityManager.clear();
    }

    private void registra(String origin, String destination, Instant cuando) {
        adapter.save(SearchRecord.of(userId, origin, destination, cuando));
        sincronizaConLaBase();
        adapter.deleteOlderBeyond(userId, SearchRecord.MAX_PER_USER);
        sincronizaConLaBase();
    }

    @Test
    void el_indice_del_historial_existe_con_el_nombre_que_cita_el_repositorio() {
        // El javadoc de SpringDataSearchHistoryRepository nombra este indice al
        // explicar por que sus dos consultas no ordenan nada despues de leer. Si el
        // indice se renombra y este test no se entera, ese javadoc queda mintiendo.
        Object nombre = entityManager.createNativeQuery("""
                SELECT indexname FROM pg_indexes
                 WHERE tablename = 'search_history'
                   AND indexname = 'ix_search_history_user_created'
                """).getSingleResult();

        assertThat(nombre).isEqualTo("ix_search_history_user_created");
    }

    @Test
    void guarda_una_busqueda_y_la_relee_con_el_id_que_asigno_la_base() {
        SearchRecord guardado = adapter.save(
                SearchRecord.of(userId, "Maiz Amarillo", "San Antonio", AHORA));
        sincronizaConLaBase();

        assertThat(guardado.id()).isNotNull();

        List<SearchRecord> historial = adapter.findRecentByUser(userId, SearchRecord.MAX_PER_USER);
        assertThat(historial).singleElement().satisfies(record -> {
            assertThat(record.origin()).isEqualTo("Maiz Amarillo");
            assertThat(record.destination()).isEqualTo("San Antonio");
            assertThat(record.searchedAt()).isEqualTo(AHORA);
        });
    }

    @Test
    void el_historial_llega_de_la_mas_reciente_a_la_mas_antigua() {
        registra("Centro", "Terminal", AHORA.minusSeconds(120));
        registra("Maiz Amarillo", "San Antonio", AHORA.minusSeconds(60));
        registra("Pekin", "Balmoral", AHORA);

        assertThat(adapter.findRecentByUser(userId, SearchRecord.MAX_PER_USER))
                .extracting(SearchRecord::origin)
                .containsExactly("Pekin", "Maiz Amarillo", "Centro");
    }

    @Test
    void al_registrar_la_septima_busqueda_se_elimina_la_mas_antigua() {
        // El criterio de aceptacion de SCRUM-80, verificado contra la base real: con
        // seis guardadas, la septima no hace crecer la tabla y la primera desaparece.
        for (int i = 0; i < SearchRecord.MAX_PER_USER; i++) {
            registra("Origen " + i, "Destino " + i, AHORA.minusSeconds(600L - i * 60L));
        }

        assertThat(adapter.findRecentByUser(userId, SearchRecord.MAX_PER_USER)).hasSize(6);

        registra("Pekin", "Balmoral", AHORA);

        List<SearchRecord> historial = adapter.findRecentByUser(userId, SearchRecord.MAX_PER_USER);
        assertThat(historial).hasSize(SearchRecord.MAX_PER_USER);
        assertThat(historial).extracting(SearchRecord::origin).first().isEqualTo("Pekin");
        assertThat(historial).extracting(SearchRecord::origin).doesNotContain("Origen 0");
    }

    @Test
    void dos_busquedas_en_el_mismo_instante_se_desempatan_por_id() {
        // Es el caso que la subconsulta de deleteOlderBeyond existe para resolver: un
        // corte por fecha borraria las dos o ninguna. Se guardan siete con el MISMO
        // created_at, asi que lo unico que puede decidir cual sobra es el id.
        for (int i = 0; i < SearchRecord.MAX_PER_USER + 1; i++) {
            registra("Origen " + i, "Destino " + i, AHORA);
        }

        List<SearchRecord> historial = adapter.findRecentByUser(userId, SearchRecord.MAX_PER_USER);

        assertThat(historial).hasSize(SearchRecord.MAX_PER_USER);
        assertThat(historial).extracting(SearchRecord::origin).doesNotContain("Origen 0");
        assertThat(historial).extracting(SearchRecord::origin).first().isEqualTo("Origen 6");
    }

    @Test
    void el_historial_de_un_usuario_no_incluye_busquedas_de_otro() {
        Long otro = ((Number) entityManager.createNativeQuery("""
                INSERT INTO users (name, email, password_hash, role, active)
                VALUES ('Beto', 'beto.historial@x.co', '$2a$12$hash', 'USER', TRUE)
                RETURNING id
                """).getSingleResult()).longValue();

        registra("Centro", "Terminal", AHORA);
        adapter.save(SearchRecord.of(otro, "Pekin", "Balmoral", AHORA));
        sincronizaConLaBase();

        assertThat(adapter.findRecentByUser(userId, SearchRecord.MAX_PER_USER))
                .extracting(SearchRecord::origin)
                .containsExactly("Centro");
    }

    @Test
    void borrar_el_usuario_se_lleva_su_historial() {
        // ON DELETE CASCADE de la V17: el historial es dato derivado de la actividad
        // del usuario, no un registro que deba sobrevivirle.
        registra("Centro", "Terminal", AHORA);

        entityManager.createNativeQuery("DELETE FROM users WHERE id = :id")
                .setParameter("id", userId).executeUpdate();
        sincronizaConLaBase();

        Number quedan = (Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM search_history WHERE user_id = :id")
                .setParameter("id", userId).getSingleResult();

        assertThat(quedan.longValue()).isZero();
    }
}
