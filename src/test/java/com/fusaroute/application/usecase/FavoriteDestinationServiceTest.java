package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.InvalidFavoriteDestinationException;
import com.fusaroute.domain.model.FavoriteDestination;
import com.fusaroute.domain.port.out.FavoriteDestinationRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteDestinationServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T09:15:00Z");

    @Mock
    private FavoriteDestinationRepositoryPort repository;

    @Captor
    private ArgumentCaptor<FavoriteDestination> guardado;

    private final Clock reloj = Clock.fixed(AHORA, ZoneId.of("America/Bogota"));

    private FavoriteDestinationService service() {
        return new FavoriteDestinationService(repository, reloj);
    }

    @Test
    void marca_el_favorito_con_el_instante_del_reloj_inyectado() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        service().set(7L, "Maiz Amarillo");

        verify(repository).save(guardado.capture());
        assertThat(guardado.getValue().userId()).isEqualTo(7L);
        assertThat(guardado.getValue().destination()).isEqualTo("Maiz Amarillo");
        assertThat(guardado.getValue().savedAt()).isEqualTo(AHORA);
    }

    @Test
    void marcar_uno_nuevo_no_consulta_antes_si_ya_habia_otro() {
        // Un solo favorito por cuenta: el guardado reemplaza porque la llave primaria
        // de la tabla es el usuario. Preguntar antes seria una ida a la base que no
        // cambia lo que se hace despues.
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        service().set(7L, "San Antonio");

        verify(repository, never()).findByUser(any());
        verify(repository).save(any());
    }

    @Test
    void devuelve_el_favorito_tal_como_quedo_guardado() {
        FavoriteDestination persistido = new FavoriteDestination(7L, "Centro", AHORA);
        when(repository.save(any())).thenReturn(persistido);

        assertThat(service().set(7L, "Centro")).isEqualTo(persistido);
    }

    @Test
    void un_destino_invalido_no_llega_a_la_base() {
        // La validacion vive en el dominio y corta antes del puerto: una fila con el
        // destino vacio nunca se intenta escribir.
        assertThatThrownBy(() -> service().set(7L, "  "))
                .isInstanceOf(InvalidFavoriteDestinationException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void consultar_devuelve_el_favorito_del_usuario() {
        FavoriteDestination favorito = new FavoriteDestination(7L, "Centro", AHORA);
        when(repository.findByUser(7L)).thenReturn(Optional.of(favorito));

        assertThat(service().of(7L)).contains(favorito);
    }

    @Test
    void un_usuario_sin_favorito_devuelve_vacio_y_no_null() {
        // No tener favorito es el estado normal de una cuenta nueva, no un error.
        when(repository.findByUser(99L)).thenReturn(Optional.empty());

        assertThat(service().of(99L)).isEmpty();
    }

    @Test
    void quitar_el_favorito_delega_en_el_puerto() {
        service().clear(7L);

        verify(repository).deleteByUser(7L);
    }
}
