package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Feedback;
import com.fusaroute.domain.model.FeedbackStatus;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de integracion de {@link FeedbackPersistenceAdapter} contra un Postgres real
 * (RF-08 y RF-14, SCRUM-22). Verifica lo que depende de la base y no del codigo:
 *
 * <ul>
 *   <li>que la migracion V19 aplique y el mapeo JPA valide con {@code ddl-auto=validate};</li>
 *   <li>que el {@code CHECK} de longitud y el de coherencia del estado existan de verdad
 *       y no solo en el dominio;</li>
 *   <li>que el listado salga del mas reciente al mas antiguo;</li>
 *   <li>que los conteos de pendientes y de respuestas nuevas cuenten lo que dicen;</li>
 *   <li>que {@code ON DELETE CASCADE} se lleve los comentarios con el usuario.</li>
 * </ul>
 *
 * {@code disabledWithoutDocker = true}: se salta solo si no hay Docker, sin romper el
 * build en una maquina sin el daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FeedbackPersistenceAdapter.class)
@Testcontainers(disabledWithoutDocker = true)
class FeedbackPersistenceAdapterIT {

    private static final Instant ESCRITO = Instant.parse("2026-11-07T09:00:00Z");

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
    private FeedbackPersistenceAdapter adapter;

    @PersistenceContext
    private EntityManager entityManager;

    private Long userId;

    /**
     * {@code feedback.user_id} tiene FK contra {@code users}, asi que sin fila duena no
     * se puede insertar nada. Se inserta con SQL nativo y no con UserPersistenceAdapter
     * para no arrastrar aqui el hasheo y el cifrado del secreto TOTP, ajenos a lo que
     * este test verifica. Mismo criterio que el IT del destino favorito.
     */
    @BeforeEach
    void creaElUsuarioAutorDelComentario() {
        userId = insertaUsuario("Ana", "ana.comentario@x.co");
    }

    private Long insertaUsuario(String nombre, String correo) {
        return ((Number) entityManager.createNativeQuery("""
                INSERT INTO users (name, email, password_hash, role, active)
                VALUES (:nombre, :correo, '$2a$12$hash', 'USER', TRUE)
                RETURNING id
                """)
                .setParameter("nombre", nombre)
                .setParameter("correo", correo)
                .getSingleResult()).longValue();
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
                "SELECT count(*) FROM feedback WHERE user_id = :id")
                .setParameter("id", userId).getSingleResult()).longValue();
    }

    private Feedback nuevo(Long autor, String texto, Instant cuando) {
        return Feedback.create(autor, "Ana", texto, Set.of(), cuando);
    }

    // --- Guardar y releer -------------------------------------------------------

    @Test
    void guarda_un_comentario_y_lo_relee_completo() {
        Feedback guardado = adapter.save(nuevo(userId, "La ruta deberia pasar mas seguido", ESCRITO));
        sincronizaConLaBase();

        assertThat(guardado.getId()).isNotNull();
        assertThat(adapter.findById(guardado.getId())).hasValueSatisfying(leido -> {
            assertThat(leido.getUserId()).isEqualTo(userId);
            assertThat(leido.getAuthorName()).isEqualTo("Ana");
            assertThat(leido.getText()).isEqualTo("La ruta deberia pasar mas seguido");
            assertThat(leido.getStatus()).isEqualTo(FeedbackStatus.PENDING);
            assertThat(leido.getReply()).isNull();
            assertThat(leido.getAnsweredAt()).isNull();
            assertThat(leido.getCreatedAt()).isEqualTo(ESCRITO);
        });
    }

    @Test
    void un_comentario_que_no_existe_devuelve_vacio() {
        assertThat(adapter.findById(404L)).isEmpty();
    }

    /**
     * El estado se guarda como texto legible ('PENDING'/'ANSWERED') y no como un
     * numero: es lo que valida el CHECK de la V19 y lo que se ve al consultar la tabla
     * a mano. Con un ordinal, reordenar el enum cambiaria el significado de las filas
     * ya guardadas.
     */
    @Test
    void el_estado_se_guarda_como_texto_legible() {
        Feedback guardado = adapter.save(nuevo(userId, "Comentario cualquiera valido", ESCRITO));
        sincronizaConLaBase();

        Object columna = entityManager.createNativeQuery(
                "SELECT status FROM feedback WHERE id = :id")
                .setParameter("id", guardado.getId()).getSingleResult();

        assertThat(columna).isEqualTo("PENDING");
    }

    // --- Editar y responder reusan el mismo save --------------------------------

    @Test
    void editar_actualiza_la_fila_y_no_inserta_otra() {
        Feedback guardado = adapter.save(nuevo(userId, "Texto original del comentario", ESCRITO));
        sincronizaConLaBase();

        adapter.save(guardado.editedTo("Texto corregido del comentario", Set.of(), ESCRITO.plusSeconds(60)));
        sincronizaConLaBase();

        assertThat(filasDelUsuario()).isEqualTo(1);
        assertThat(adapter.findById(guardado.getId())).hasValueSatisfying(leido -> {
            assertThat(leido.getText()).isEqualTo("Texto corregido del comentario");
            assertThat(leido.getUpdatedAt()).isEqualTo(ESCRITO.plusSeconds(60));
        });
    }

    @Test
    void responder_guarda_la_respuesta_y_el_estado_en_la_misma_fila() {
        Feedback guardado = adapter.save(nuevo(userId, "La ruta no pasa por mi barrio", ESCRITO));
        sincronizaConLaBase();

        Instant respondidoEn = ESCRITO.plusSeconds(3600);
        adapter.save(guardado.answeredWith("Ya agregamos el barrio al recorrido", respondidoEn));
        sincronizaConLaBase();

        assertThat(filasDelUsuario()).isEqualTo(1);
        assertThat(adapter.findById(guardado.getId())).hasValueSatisfying(leido -> {
            assertThat(leido.getStatus()).isEqualTo(FeedbackStatus.ANSWERED);
            assertThat(leido.getReply()).isEqualTo("Ya agregamos el barrio al recorrido");
            assertThat(leido.getAnsweredAt()).isEqualTo(respondidoEn);
        });
    }

    // --- Listado ----------------------------------------------------------------

    @Test
    void el_listado_del_usuario_va_del_mas_reciente_al_mas_antiguo() {
        adapter.save(nuevo(userId, "El comentario mas viejo de todos", ESCRITO.minusSeconds(7200)));
        adapter.save(nuevo(userId, "El comentario del medio de todos", ESCRITO.minusSeconds(3600)));
        adapter.save(nuevo(userId, "El comentario mas reciente de todos", ESCRITO));
        sincronizaConLaBase();

        assertThat(adapter.findByUser(userId))
                .extracting(Feedback::getText)
                .containsExactly(
                        "El comentario mas reciente de todos",
                        "El comentario del medio de todos",
                        "El comentario mas viejo de todos");
    }

    @Test
    void el_listado_de_un_usuario_no_trae_los_comentarios_de_otro() {
        Long otro = insertaUsuario("Beto", "beto.comentario@x.co");
        adapter.save(nuevo(userId, "Comentario escrito por Ana", ESCRITO));
        adapter.save(nuevo(otro, "Comentario escrito por Beto", ESCRITO));
        sincronizaConLaBase();

        assertThat(adapter.findByUser(userId))
                .extracting(Feedback::getText)
                .containsExactly("Comentario escrito por Ana");
    }

    @Test
    void un_usuario_sin_comentarios_devuelve_lista_vacia() {
        assertThat(adapter.findByUser(userId)).isEmpty();
    }

    // --- Borrar -----------------------------------------------------------------

    @Test
    void borrar_quita_la_fila() {
        Feedback guardado = adapter.save(nuevo(userId, "Comentario que se va a borrar", ESCRITO));
        sincronizaConLaBase();

        adapter.delete(guardado.getId());
        sincronizaConLaBase();

        assertThat(filasDelUsuario()).isZero();
    }

    @Test
    void borrar_el_usuario_se_lleva_sus_comentarios() {
        // ON DELETE CASCADE de la V19: sin perfil no hay a quien atribuir el comentario
        // y un comentario huerfano en el panel del administrador no se puede responder.
        adapter.save(nuevo(userId, "Comentario que debe irse con la cuenta", ESCRITO));
        sincronizaConLaBase();

        entityManager.createNativeQuery("DELETE FROM users WHERE id = :id")
                .setParameter("id", userId).executeUpdate();
        sincronizaConLaBase();

        assertThat(filasDelUsuario()).isZero();
    }

    // --- Listado del administrador (SCRUM-98) -----------------------------------

    /**
     * "Pendientes primero" verificado contra Postgres, que es el unico lugar donde se
     * puede verificar: el orden lo decide la consulta con un CASE, no el codigo Java.
     *
     * El caso que importa es el del medio: el respondido es el MAS RECIENTE de los
     * tres, asi que un ORDER BY solo por fecha lo pondria de primero. Tiene que salir
     * ultimo.
     */
    @Test
    void el_listado_del_administrador_pone_los_pendientes_primero() {
        adapter.save(nuevo(userId, "Pendiente mas viejo de los tres", ESCRITO.minusSeconds(7200)));
        adapter.save(nuevo(userId, "Pendiente mas nuevo de los tres", ESCRITO.minusSeconds(3600)));
        Feedback porResponder = adapter.save(nuevo(userId, "El que sera respondido", ESCRITO));
        sincronizaConLaBase();

        adapter.save(porResponder.answeredWith("Ya lo revisamos", ESCRITO.plusSeconds(60)));
        sincronizaConLaBase();

        assertThat(adapter.findAllPendingFirst())
                .extracting(Feedback::getText)
                .containsExactly(
                        "Pendiente mas nuevo de los tres",
                        "Pendiente mas viejo de los tres",
                        "El que sera respondido");
    }

    @Test
    void el_listado_del_administrador_trae_los_comentarios_de_todos_los_usuarios() {
        Long otro = insertaUsuario("Beto", "beto.panel@x.co");
        adapter.save(nuevo(userId, "Comentario escrito por Ana", ESCRITO));
        adapter.save(nuevo(otro, "Comentario escrito por Beto", ESCRITO.minusSeconds(60)));
        sincronizaConLaBase();

        assertThat(adapter.findAllPendingFirst()).hasSize(2);
    }

    @Test
    void el_filtro_por_estado_solo_trae_ese_estado() {
        adapter.save(nuevo(userId, "Comentario que queda pendiente", ESCRITO.minusSeconds(60)));
        Feedback porResponder = adapter.save(nuevo(userId, "Comentario que sera respondido", ESCRITO));
        sincronizaConLaBase();

        adapter.save(porResponder.answeredWith("Ya lo revisamos", ESCRITO.plusSeconds(60)));
        sincronizaConLaBase();

        assertThat(adapter.findAllByStatus(FeedbackStatus.PENDING))
                .extracting(Feedback::getText)
                .containsExactly("Comentario que queda pendiente");
        assertThat(adapter.findAllByStatus(FeedbackStatus.ANSWERED))
                .extracting(Feedback::getText)
                .containsExactly("Comentario que sera respondido");
    }

    @Test
    void el_filtro_por_estado_va_del_mas_reciente_al_mas_antiguo() {
        adapter.save(nuevo(userId, "El pendiente mas viejo aqui", ESCRITO.minusSeconds(3600)));
        adapter.save(nuevo(userId, "El pendiente mas nuevo aqui", ESCRITO));
        sincronizaConLaBase();

        assertThat(adapter.findAllByStatus(FeedbackStatus.PENDING))
                .extracting(Feedback::getText)
                .containsExactly("El pendiente mas nuevo aqui", "El pendiente mas viejo aqui");
    }

    @Test
    void sin_comentarios_los_dos_listados_del_administrador_son_vacios() {
        assertThat(adapter.findAllPendingFirst()).isEmpty();
        assertThat(adapter.findAllByStatus(FeedbackStatus.PENDING)).isEmpty();
    }

    // --- Conteos ----------------------------------------------------------------

    @Test
    void el_conteo_de_pendientes_no_cuenta_los_respondidos() {
        adapter.save(nuevo(userId, "Comentario pendiente numero uno", ESCRITO));
        adapter.save(nuevo(userId, "Comentario pendiente numero dos", ESCRITO.plusSeconds(1)));
        Feedback tercero = adapter.save(nuevo(userId, "Comentario que sera respondido", ESCRITO.plusSeconds(2)));
        sincronizaConLaBase();

        adapter.save(tercero.answeredWith("Ya lo revisamos", ESCRITO.plusSeconds(60)));
        sincronizaConLaBase();

        // Metrica 4 del panel de RF-13.
        assertThat(adapter.countPending()).isEqualTo(2);
    }

    @Test
    void el_conteo_de_respuestas_nuevas_solo_cuenta_las_posteriores_al_corte() {
        Feedback viejo = adapter.save(nuevo(userId, "Comentario respondido hace tiempo", ESCRITO));
        Feedback reciente = adapter.save(nuevo(userId, "Comentario respondido hace poco", ESCRITO));
        sincronizaConLaBase();

        Instant corte = ESCRITO.plusSeconds(1800);
        adapter.save(viejo.answeredWith("Respuesta vieja", corte.minusSeconds(600)));
        adapter.save(reciente.answeredWith("Respuesta nueva", corte.plusSeconds(600)));
        sincronizaConLaBase();

        assertThat(adapter.countAnsweredSince(userId, corte)).isEqualTo(1);
    }

    @Test
    void el_conteo_de_respuestas_nuevas_no_cuenta_las_de_otro_usuario() {
        Long otro = insertaUsuario("Beto", "beto.conteo@x.co");
        Feedback deBeto = adapter.save(nuevo(otro, "Comentario de Beto respondido", ESCRITO));
        sincronizaConLaBase();

        adapter.save(deBeto.answeredWith("Respuesta a Beto", ESCRITO.plusSeconds(600)));
        sincronizaConLaBase();

        assertThat(adapter.countAnsweredSince(userId, ESCRITO)).isZero();
        assertThat(adapter.countAnsweredSince(otro, ESCRITO)).isEqualTo(1);
    }

    // --- Las restricciones de la V19 existen de verdad --------------------------

    /**
     * El CHECK de longitud no es redundante con el dominio: ataja la carga de datos a
     * mano y los scripts de siembra, que no pasan por Java. Se verifica con SQL nativo
     * justamente porque el dominio nunca dejaria construir este valor.
     */
    @Test
    void la_base_rechaza_un_comentario_mas_corto_que_el_minimo() {
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("""
                    INSERT INTO feedback (user_id, author_name, text, status)
                    VALUES (:id, 'Ana', 'corto', 'PENDING')
                    """).setParameter("id", userId).executeUpdate();
            entityManager.flush();
        }).hasStackTraceContaining("ck_feedback_text_length");
    }

    @Test
    void la_base_rechaza_un_estado_que_no_existe() {
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("""
                    INSERT INTO feedback (user_id, author_name, text, status)
                    VALUES (:id, 'Ana', 'Comentario perfectamente valido', 'ARCHIVADO')
                    """).setParameter("id", userId).executeUpdate();
            entityManager.flush();
        }).hasStackTraceContaining("violates check constraint");
    }

    /**
     * Una fila 'ANSWERED' sin respuesta no se puede pintar, y una 'PENDING' con
     * respuesta mentiria al conteo de pendientes del panel de RF-13. El dominio ya lo
     * valida; el CHECK lo vuelve imposible, que es distinto.
     */
    @Test
    void la_base_rechaza_un_respondido_sin_respuesta() {
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("""
                    INSERT INTO feedback (user_id, author_name, text, status)
                    VALUES (:id, 'Ana', 'Comentario perfectamente valido', 'ANSWERED')
                    """).setParameter("id", userId).executeUpdate();
            entityManager.flush();
        }).hasStackTraceContaining("ck_feedback_reply_coherente");
    }

    @Test
    void la_base_rechaza_un_pendiente_con_respuesta() {
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("""
                    INSERT INTO feedback (user_id, author_name, text, status, reply, answered_at)
                    VALUES (:id, 'Ana', 'Comentario perfectamente valido', 'PENDING', 'respuesta', now())
                    """).setParameter("id", userId).executeUpdate();
            entityManager.flush();
        }).hasStackTraceContaining("ck_feedback_reply_coherente");
    }

    /**
     * Las dos columnas de ingreso de la V19 tienen que existir y arrancar en NULL: es
     * lo que significa "este usuario no ha iniciado sesion nunca", y de lo que depende
     * que el contador devuelva 0 en el primer ingreso en vez de contar todo.
     */
    @Test
    void las_columnas_de_ingreso_arrancan_en_nulo() {
        Object[] fila = (Object[]) entityManager.createNativeQuery(
                "SELECT last_login_at, previous_login_at FROM users WHERE id = :id")
                .setParameter("id", userId).getSingleResult();

        assertThat(fila[0]).isNull();
        assertThat(fila[1]).isNull();
    }
}
