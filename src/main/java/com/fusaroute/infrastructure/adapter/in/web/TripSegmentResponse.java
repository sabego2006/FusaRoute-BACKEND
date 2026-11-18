package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.TripSegment;

import java.util.List;

/**
 * El tramo que la persona recorre de verdad (SCRUM-198): lo que el resultado de la busqueda debe
 * mostrar en vez de la ruta completa.
 *
 * Es un campo NUEVO y opcional de {@code RouteSearchResponse}: un cliente anterior a esta historia lo
 * ignora y sigue funcionando, y un cliente nuevo contra un backend anterior no recibe la clave y cae
 * al comportamiento honesto de "no hay detalle del tramo".
 *
 * @param ridePath pedazo del trazado entre la subida y la bajada, como LineString de GeoJSON
 *     ({@code [longitud, latitud]}, igual que {@code path}); {@code null} si la ruta no tiene trazado
 *     y entonces no se sabe cual es el tramo. NO es la ruta completa: para eso esta {@code path}
 * @param neighborhoods barrios que la buseta pasa dentro del tramo, en orden de recorrido. Lista
 *     VACIA es una respuesta valida y frecuente — con recorridos cortos no hay ningun barrio entre la
 *     subida y la bajada —, no un error ni un "todavia no se calculo"
 * @param boardingWalk como se explica la caminata hasta la subida
 * @param dropOffWalk como se explica la caminata desde la bajada hasta el destino
 */
public record TripSegmentResponse(GeoJsonLineStringResponse ridePath, List<String> neighborhoods,
                                  WalkGuideResponse boardingWalk, WalkGuideResponse dropOffWalk) {

    /**
     * Hacia donde va una caminata y hasta que sitio reconocible llega.
     *
     * @param bearingDegrees rumbo desde el norte y en sentido horario (0 = norte, 90 = oriente),
     *     hacia el punto al que se camina. {@code null} si los dos puntos estan a menos de 20 m y no
     *     hay direccion que tenga sentido dar. Van los GRADOS y no la palabra: con que palabra se
     *     dice ("hacia el nororiente") lo decide el cliente, que es quien conoce el idioma de la
     *     pantalla
     * @param reference nombre del lugar del catalogo mas cercano a ese punto; {@code null} si ninguno
     *     queda a menos de 300 m, porque nombrar uno mas lejos manda a la persona a otra esquina
     */
    public record WalkGuideResponse(Double bearingDegrees, String reference) {

        static WalkGuideResponse from(TripSegment.WalkGuide guide) {
            return new WalkGuideResponse(guide.bearingDegrees(), guide.reference());
        }
    }

    static TripSegmentResponse from(TripSegment segment) {
        return new TripSegmentResponse(
                segment.hasRidePath() ? GeoJsonLineStringResponse.from(segment.ridePath()) : null,
                segment.neighborhoods(),
                WalkGuideResponse.from(segment.boardingWalk()),
                WalkGuideResponse.from(segment.dropOffWalk()));
    }
}
