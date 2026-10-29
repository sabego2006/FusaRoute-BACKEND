package com.fusaroute.infrastructure.adapter.out.maps;

import java.util.List;

/**
 * Subconjunto de la respuesta de la Distance Matrix API de Google que el
 * adaptador necesita. El resto del payload (direcciones formateadas, distancia
 * en texto, etc.) no se mapea porque no se usa.
 *
 * {@code error_message} viaja aparte del {@code status} de negocio: Google lo
 * incluye cuando el status NO es {@code OK} (p. ej. {@code REQUEST_DENIED},
 * {@code OVER_QUERY_LIMIT}) con el motivo exacto en texto. Sin este campo, el
 * adaptador solo reportaba el status y dejaba al operador adivinando si era
 * clave invalida, API no habilitada o restriccion de dominio. Nunca contiene
 * la clave, pero igualmente se filtra en {@link GoogleMapsClient} para no
 * propagar informacion sensible por error.
 */
record DistanceMatrixResponse(String status,
                              @com.fasterxml.jackson.annotation.JsonProperty("error_message") String errorMessage,
                              List<Row> rows) {

    record Row(List<Element> elements) {
    }

    record Element(String status, ApiDuration duration) {
    }

    /** Nombrado distinto de java.time.Duration para no chocar con ese import. */
    record ApiDuration(long value) {
    }
}
