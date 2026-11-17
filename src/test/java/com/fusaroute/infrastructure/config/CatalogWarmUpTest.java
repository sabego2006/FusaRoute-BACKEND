package com.fusaroute.infrastructure.config;

import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import com.fusaroute.domain.port.in.ListPlacesUseCase;
import com.fusaroute.domain.port.out.TrafficSectorRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogWarmUpTest {

    private final ListActiveRoutesUseCase routes = mock(ListActiveRoutesUseCase.class);
    private final ListPlacesUseCase places = mock(ListPlacesUseCase.class);
    private final TrafficSectorRepositoryPort sectors = mock(TrafficSectorRepositoryPort.class);
    private final CatalogWarmUp warmUp = new CatalogWarmUp(routes, places, sectors);

    @Test
    void al_arrancar_deja_cargados_rutas_lugares_y_sectores() {
        when(routes.listActive()).thenReturn(List.of());
        when(places.listPlaces()).thenReturn(List.of());
        when(sectors.findAll()).thenReturn(List.of());

        warmUp.warmUp();

        verify(routes).listActive();
        verify(places).listPlaces();
        verify(sectors).findAll();
    }

    @Test
    void si_la_base_no_responde_la_aplicacion_arranca_igual() {
        // Precargar es una optimizacion: un fallo aqui no puede impedir el arranque.
        when(routes.listActive()).thenThrow(new IllegalStateException("base caida"));

        assertThatCode(warmUp::warmUp).doesNotThrowAnyException();
    }
}
