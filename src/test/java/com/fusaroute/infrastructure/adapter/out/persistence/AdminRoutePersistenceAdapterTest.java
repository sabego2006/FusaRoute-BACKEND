package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.exception.RouteConflictException;
import com.fusaroute.domain.model.AdminRoute;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.FareChange;
import com.fusaroute.domain.model.FareDraft;
import com.fusaroute.domain.model.RouteDraft;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El SQL en si solo lo prueba una base real (hace falta Docker); aqui se verifica el
 * orden de las escrituras, el mapeo de filas y la traduccion del conflicto de unicidad.
 */
@ExtendWith(MockitoExtension.class)
// Lenient: las lecturas (query con callback o con mapper) se simulan por separado segun la prueba.
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminRoutePersistenceAdapterTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);

    @Mock
    private JdbcTemplate jdbc;

    private AdminRoutePersistenceAdapter adapter() {
        return new AdminRoutePersistenceAdapter(jdbc);
    }

    private static RouteDraft borrador(boolean conTrazado) {
        return new RouteDraft("Centro - Terminal", RouteType.URBANA, conTrazado ? "LineString" : null,
                conTrazado ? List.of(List.of(-74.37, 4.34), List.of(-74.36, 4.35)) : null,
                List.of("Centro", " Terminal "), List.of(new FareDraft(new BigDecimal("2600"), null)),
                "CENTRO_TERMINAL");
    }

    /** Hace que las tres consultas de lectura devuelvan una ruta con dos barrios y una tarifa. */
    private void stubLectura() throws SQLException {
        ResultSet barrios = mock(ResultSet.class);
        lenient().when(barrios.getLong("route_id")).thenReturn(8L);
        lenient().when(barrios.getString("neighborhood_name")).thenReturn("Centro", "Terminal");
        doAnswer(inv -> {
            RowCallbackHandler handler = inv.getArgument(1);
            handler.processRow(barrios);
            handler.processRow(barrios);
            return null;
        }).when(jdbc).query(contains("route_neighborhoods"), any(RowCallbackHandler.class), any(Object[].class));

        ResultSet tarifas = mock(ResultSet.class);
        when(tarifas.getLong("route_id")).thenReturn(8L);
        when(tarifas.getString("reference_point")).thenReturn(null);
        when(tarifas.getBigDecimal("amount")).thenReturn(new BigDecimal("2600.00"));
        when(tarifas.getInt("order_index")).thenReturn(0);
        when(tarifas.getDate("valid_from")).thenReturn(Date.valueOf(HOY));
        doAnswer(inv -> {
            RowCallbackHandler handler = inv.getArgument(1);
            handler.processRow(tarifas);
            return null;
        }).when(jdbc).query(contains("FROM fares"), any(RowCallbackHandler.class), any(Object[].class));

        ResultSet rutas = mock(ResultSet.class);
        when(rutas.getLong("id")).thenReturn(8L);
        when(rutas.getString("name")).thenReturn("Centro - Terminal");
        when(rutas.getString("route_type")).thenReturn("URBANA");
        when(rutas.getString("status")).thenReturn("INACTIVA");
        when(rutas.getString("sign_file")).thenReturn("CENTRO_TERMINAL");
        when(rutas.getString("path")).thenReturn(null);
        when(rutas.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.parse("2026-11-12T15:00:00Z")));
        when(jdbc.query(contains("FROM routes"), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
            RowMapper<AdminRoute> mapper = inv.getArgument(1);
            return List.of(mapper.mapRow(rutas, 0));
        });
    }

    @Test
    void lee_la_ruta_con_barrios_en_orden_y_todas_sus_tarifas() throws SQLException {
        stubLectura();

        Optional<AdminRoute> ruta = adapter().findById(8L);

        assertThat(ruta).isPresent();
        assertThat(ruta.get().status()).isEqualTo(RouteStatus.INACTIVA);
        assertThat(ruta.get().hasPath()).isFalse();
        assertThat(ruta.get().neighborhoods()).containsExactly("Centro", "Terminal");
        assertThat(ruta.get().fares()).containsExactly(new Fare(null, new BigDecimal("2600.00"), 0, HOY));
        assertThat(ruta.get().updatedAt()).isEqualTo(Instant.parse("2026-11-12T15:00:00Z"));
    }

    @Test
    void el_listado_lee_todas_las_rutas() throws SQLException {
        stubLectura();

        assertThat(adapter().findAll()).extracting(AdminRoute::id).containsExactly(8L);
    }

    @Test
    void una_ruta_sin_filas_es_vacio() {
        when(jdbc.query(contains("FROM routes"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        assertThat(adapter().findById(99L)).isEmpty();
    }

    @Test
    void existe_por_nombre_o_letrero_cuenta_filas_ignorando_la_propia_ruta() {
        when(jdbc.queryForObject(contains("lower(name)"), eq(Integer.class), any(), any())).thenReturn(1);
        when(jdbc.queryForObject(contains("sign_file"), eq(Integer.class), any(), any())).thenReturn(0);

        assertThat(adapter().existsByName("Centro", null)).isTrue();
        assertThat(adapter().existsBySignFile("CENTRO", 5L)).isFalse();
        verify(jdbc).queryForObject(anyString(), eq(Integer.class), eq("Centro"), eq(-1L));
        verify(jdbc).queryForObject(anyString(), eq(Integer.class), eq("CENTRO"), eq(5L));
    }

    @Test
    void cambia_el_estado_y_la_fecha_de_actualizacion() {
        adapter().setStatus(8L, RouteStatus.INACTIVA);

        verify(jdbc).update(contains("updated_at = now()"), eq("INACTIVA"), eq(8L));
    }

    @Test
    void crea_la_ruta_sus_barrios_sin_espacios_y_sus_tarifas() throws SQLException {
        stubLectura();
        when(jdbc.queryForObject(contains("INSERT INTO routes"), eq(Long.class), any(), any(), any(), any()))
                .thenReturn(8L);

        adapter().create(borrador(true), List.of(new Fare(null, new BigDecimal("2600"), 0, HOY)));

        verify(jdbc).queryForObject(contains("INSERT INTO routes"), eq(Long.class), eq("Centro - Terminal"),
                eq("URBANA"), eq("{\"type\":\"LineString\",\"coordinates\":[[-74.37,4.34],[-74.36,4.35]]}"),
                eq("CENTRO_TERMINAL"));
        verify(jdbc).update(contains("route_neighborhoods"), eq(8L), eq(0), eq("Centro"));
        verify(jdbc).update(contains("route_neighborhoods"), eq(8L), eq(1), eq("Terminal"));
        verify(jdbc).update(contains("ON CONFLICT ON CONSTRAINT uq_fares_route_point"), eq(8L),
                eq(new BigDecimal("2600")), eq(null), eq(0), eq(HOY));
    }

    @Test
    void crear_sin_trazado_guarda_nulo() throws SQLException {
        stubLectura();
        when(jdbc.queryForObject(contains("INSERT INTO routes"), eq(Long.class), any(), any(), any(), any()))
                .thenReturn(8L);

        adapter().create(borrador(false), List.of());

        verify(jdbc).queryForObject(contains("INSERT INTO routes"), eq(Long.class), eq("Centro - Terminal"),
                eq("URBANA"), eq(null), eq("CENTRO_TERMINAL"));
    }

    @Test
    void crear_con_un_nombre_que_choca_en_la_base_es_conflicto() {
        when(jdbc.queryForObject(contains("INSERT INTO routes"), eq(Long.class), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("uq_routes_name"));

        assertThatThrownBy(() -> adapter().create(borrador(false), List.of()))
                .isInstanceOf(RouteConflictException.class);
        verify(jdbc, never()).update(contains("route_neighborhoods"), any(), any(), any());
    }

    @Test
    void modificar_sin_cambio_de_tarifa_no_toca_las_tarifas() throws SQLException {
        stubLectura();

        adapter().update(8L, borrador(false), new FareChange(false, List.of(), List.of()));

        verify(jdbc).update(contains("UPDATE routes SET"), eq("Centro - Terminal"), eq("URBANA"), eq(null),
                eq("CENTRO_TERMINAL"), eq(8L));
        verify(jdbc).update(contains("DELETE FROM route_neighborhoods"), eq(8L));
        verify(jdbc, never()).update(contains("fares"), any(Object[].class));
    }

    @Test
    void modificar_con_cambio_elimina_los_puntos_que_sobran_y_escribe_las_filas_de_hoy() throws SQLException {
        stubLectura();
        FareChange cambio = new FareChange(true, List.of(new Fare(null, new BigDecimal("2800"), 0, HOY)),
                List.of("Pasca"));

        adapter().update(8L, borrador(false), cambio);

        verify(jdbc).update(contains("DELETE FROM fares"), eq(8L), eq("Pasca"));
        verify(jdbc).update(contains("ON CONFLICT ON CONSTRAINT uq_fares_route_point"), eq(8L),
                eq(new BigDecimal("2800")), eq(null), eq(0), eq(HOY));
    }

    @Test
    void modificar_con_un_nombre_que_choca_en_la_base_es_conflicto() {
        when(jdbc.update(contains("UPDATE routes SET"), any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("uq_routes_sign_file"));

        assertThatThrownBy(() -> adapter().update(8L, borrador(false), new FareChange(false, List.of(), List.of())))
                .isInstanceOf(RouteConflictException.class);
    }
}
