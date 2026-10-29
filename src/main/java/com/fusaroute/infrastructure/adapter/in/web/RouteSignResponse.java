package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.RouteSign;

import java.time.LocalDate;
import java.util.List;

/**
 * Letrero fisico de una ruta (SCRUM-176), tal como lo levanto el trabajo de campo de
 * SCRUM-174/175.
 *
 * @param fieldId trazabilidad hacia el modelado de campo (p. ej. "LC-029")
 * @param sense sentido que documenta el letrero; {@code null} si no aplica
 * @param evidenceCrop recorte de la foto de campo; {@code null} si no se recorto
 */
public record RouteSignResponse(String fieldId, String sense, String sourcePhoto, String evidenceCrop,
                                 LocalDate observedOn, List<SignPlateResponse> plates) {

    static RouteSignResponse from(RouteSign sign) {
        return new RouteSignResponse(
                sign.fieldId(),
                sign.sense() == null ? null : sign.sense().name(),
                sign.sourcePhoto(),
                sign.evidenceCrop(),
                sign.observedOn(),
                sign.plates().stream().map(SignPlateResponse::from).toList());
    }

    /** Lista vacia cuando la ruta no tiene ningun letrero todavia; no hace falta distinguir null. */
    static List<RouteSignResponse> from(List<RouteSign> signs) {
        return signs.stream().map(RouteSignResponse::from).toList();
    }
}
