package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.SearchRecord;
import com.fusaroute.domain.port.out.SearchHistoryRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchHistoryServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-29T14:30:00Z");

    @Mock
    private SearchHistoryRepositoryPort repository;

    @Captor
    private ArgumentCaptor<SearchRecord> guardado;

    private final Clock reloj = Clock.fixed(AHORA, ZoneId.of("America/Bogota"));

    private SearchHistoryService service() {
        return new SearchHistoryService(repository, reloj);
    }

    @Test
    void guarda_la_busqueda_con_el_instante_del_reloj_inyectado() {
        service().record(7L, "Maiz Amarillo", "San Antonio");

        verify(repository).save(guardado.capture());
        SearchRecord record = guardado.getValue();
        assertThat(record.userId()).isEqualTo(7L);
        assertThat(record.origin()).isEqualTo("Maiz Amarillo");
        assertThat(record.destination()).isEqualTo("San Antonio");
        assertThat(record.searchedAt()).isEqualTo(AHORA);
        assertThat(record.id()).isNull();
    }

    @Test
    void recorta_el_historial_al_tope_despues_de_guardar() {
        service().record(7L, "Siboney", "Centro");

        // El orden importa: si se recortara antes de guardar y el guardado fallara,
        // el usuario perderia una busqueda sin ganar ninguna.
        var orden = inOrder(repository);
        orden.verify(repository).save(any(SearchRecord.class));
        orden.verify(repository).deleteOlderBeyond(7L, 6);
    }

    @Test
    void el_tope_del_historial_es_seis() {
        assertThat(SearchRecord.MAX_PER_USER).isEqualTo(6);
    }

    @Test
    void devuelve_el_historial_del_usuario_limitado_al_tope() {
        List<SearchRecord> esperado = List.of(
                new SearchRecord(2L, 7L, "Siboney", "Centro", AHORA),
                new SearchRecord(1L, 7L, "Gaitan", "Terminal", AHORA.minusSeconds(60)));
        when(repository.findRecentByUser(7L, 6)).thenReturn(esperado);

        assertThat(service().historyOf(7L)).isEqualTo(esperado);
    }

    @Test
    void el_historial_de_un_usuario_sin_busquedas_llega_vacio() {
        when(repository.findRecentByUser(99L, 6)).thenReturn(List.of());

        assertThat(service().historyOf(99L)).isEmpty();
    }

    @Test
    void rechaza_una_busqueda_sin_usuario() {
        assertThatThrownBy(() -> SearchRecord.of(null, "Gaitan", "Centro", AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usuario");
    }

    @Test
    void rechaza_origen_o_destino_vacios() {
        assertThatThrownBy(() -> SearchRecord.of(7L, "   ", "Centro", AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("origen");

        assertThatThrownBy(() -> SearchRecord.of(7L, "Gaitan", null, AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destino");
    }

    @Test
    void recorta_los_espacios_de_los_barrios() {
        SearchRecord record = SearchRecord.of(7L, "  Gaitan  ", "  Centro ", AHORA);

        assertThat(record.origin()).isEqualTo("Gaitan");
        assertThat(record.destination()).isEqualTo("Centro");
    }
}
