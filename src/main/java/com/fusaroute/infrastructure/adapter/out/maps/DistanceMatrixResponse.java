package com.fusaroute.infrastructure.adapter.out.maps;

import java.util.List;

/**
 * Subconjunto de la respuesta de la Distance Matrix API de Google que el
 * adaptador necesita. El resto del payload (direcciones formateadas, distancia
 * en texto, etc.) no se mapea porque no se usa.
 */
record DistanceMatrixResponse(String status, List<Row> rows) {

    record Row(List<Element> elements) {
    }

    record Element(String status, ApiDuration duration) {
    }

    /** Nombrado distinto de java.time.Duration para no chocar con ese import. */
    record ApiDuration(long value) {
    }
}
