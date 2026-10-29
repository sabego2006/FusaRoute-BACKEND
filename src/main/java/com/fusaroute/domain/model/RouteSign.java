package com.fusaroute.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Letrero fisico de una buseta, ligado a la ruta que opera (SCRUM-176). El dato viene
 * del trabajo de campo de SCRUM-174/175: una foto tomada el 27-sep-2026 y modelada a
 * mano placa por placa.
 *
 * Una ruta puede tener varios letreros — 0 si todavia no hay evidencia de campo que
 * la respalde, o varios si distintos sentidos o distintas busetas muestran placas
 * distintas (ver 'Camino Real - La Pampa' en V11, con tres). Por eso este tipo no
 * vive dentro de {@link Route}: se compone aparte, igual que {@link CongestionWarning}.
 *
 * @param fieldId identificador de campo (p. ej. "LC-029"): trazabilidad hacia
 *     {@code docs/investigacion/letreros-estilos.json} y la foto original
 * @param sense sentido que documenta el letrero; {@code null} si no aplica (la
 *     mayoria — ver {@link SignSense})
 * @param sourcePhoto nombre del archivo de la foto de campo que origino este letrero
 * @param evidenceCrop nombre del recorte de esa foto; {@code null} si no se recorto
 *     por separado
 * @param observedOn fecha en que se tomo la foto de campo
 * @param plates placas del letrero, en el orden en que aparecen en el parabrisas
 */
public record RouteSign(String fieldId, SignSense sense, String sourcePhoto, String evidenceCrop,
                        LocalDate observedOn, List<SignPlate> plates) {

    public RouteSign {
        Objects.requireNonNull(fieldId, "fieldId");
        Objects.requireNonNull(sourcePhoto, "sourcePhoto");
        Objects.requireNonNull(observedOn, "observedOn");
        plates = List.copyOf(plates);
    }
}
