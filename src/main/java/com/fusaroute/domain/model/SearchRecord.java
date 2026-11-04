package com.fusaroute.domain.model;

import java.time.Instant;

/**
 * Una busqueda que el usuario ya hizo (RF-07, SCRUM-20). Guarda solo los dos
 * barrios, no las coordenadas: el historial sirve para repetir la consulta, y la
 * pantalla vuelve a pedir el punto exacto.
 *
 * Vive en la cuenta y no en el dispositivo a proposito — el criterio de
 * aceptacion exige que al entrar desde otro telefono se vean las mismas seis.
 *
 * @param id        nulo mientras no se haya persistido.
 * @param userId    dueno del historial; nunca se comparte entre cuentas.
 * @param origin    barrio de origen tal como se busco.
 * @param destination barrio de destino tal como se busco.
 * @param searchedAt momento de la busqueda; es lo que ordena el historial.
 */
public record SearchRecord(Long id, Long userId, String origin, String destination, Instant searchedAt) {

    /**
     * Cuantas busquedas se conservan por usuario (RF-07). Al guardar la septima
     * se descarta la mas antigua.
     */
    public static final int MAX_PER_USER = 6;

    public SearchRecord {
        if (userId == null) {
            throw new IllegalArgumentException("El historial siempre pertenece a un usuario");
        }
        origin = requireNeighborhood(origin, "origen");
        destination = requireNeighborhood(destination, "destino");
        if (searchedAt == null) {
            throw new IllegalArgumentException("La busqueda necesita una fecha");
        }
    }

    /** Entrada nueva, sin id todavia. */
    public static SearchRecord of(Long userId, String origin, String destination, Instant searchedAt) {
        return new SearchRecord(null, userId, origin, destination, searchedAt);
    }

    private static String requireNeighborhood(String value, String campo) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El " + campo + " de la busqueda no puede ir vacio");
        }
        return value.trim();
    }
}
