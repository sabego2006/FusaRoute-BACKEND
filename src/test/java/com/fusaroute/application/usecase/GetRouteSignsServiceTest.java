package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.RouteSign;
import com.fusaroute.domain.model.SignLine;
import com.fusaroute.domain.model.SignPlate;
import com.fusaroute.domain.model.SignSense;
import com.fusaroute.domain.model.SignTemplate;
import com.fusaroute.domain.port.out.RouteSignRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetRouteSignsServiceTest {

    @Mock
    private RouteSignRepositoryPort routeSignRepository;

    private GetRouteSignsService service;

    @BeforeEach
    void setUp() {
        // Construido en @BeforeEach y no como inicializador de campo: @Mock se
        // inyecta despues de que corren los inicializadores de campo, asi que un
        // "new GetRouteSignsService(routeSignRepository)" ahi capturaria null.
        service = new GetRouteSignsService(routeSignRepository);
    }

    private static RouteSign letrero(String fieldId, SignSense sense) {
        return new RouteSign(fieldId, sense, "20260927_000000.jpg", "20260927_000000.jpg",
                LocalDate.of(2026, 9, 27),
                List.of(new SignPlate(SignTemplate.PLACA_ARCO, null,
                        List.of(new SignLine("CENTRO", "#F7A93B", "#4A1D06")))));
    }

    @Test
    void devuelve_los_letreros_tal_como_los_entrega_el_repositorio() {
        RouteSign subiendo = letrero("LC-008", SignSense.SUBIENDO);
        RouteSign bajando = letrero("LC-015", SignSense.BAJANDO);
        when(routeSignRepository.findByRouteId(4L)).thenReturn(List.of(subiendo, bajando));

        List<RouteSign> signs = service.forRoute(4L);

        assertThat(signs).containsExactly(subiendo, bajando);
    }

    @Test
    void una_ruta_sin_evidencia_de_campo_devuelve_lista_vacia() {
        // Es el estado real de 'Fusagasugá - Pasca', 'Gran Colombia' y 'Villa Leny -
        // SENA Quebrajacho' (V11): ningun letrero fotografiado las respalda todavia.
        when(routeSignRepository.findByRouteId(8L)).thenReturn(List.of());

        assertThat(service.forRoute(8L)).isEmpty();
    }
}
