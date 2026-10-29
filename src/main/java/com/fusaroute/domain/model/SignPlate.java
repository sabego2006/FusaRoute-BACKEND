package com.fusaroute.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Una placa fisica dentro de un letrero (SCRUM-175/176): un letrero de buseta suele
 * llevar varias placas juntas en el parabrisas, cada una con su propia forma y sus
 * propias lineas.
 *
 * @param template forma de la placa
 * @param code codigo pintado en la placa (p. ej. "P-15", "100"); {@code null} si la
 *     placa no trae codigo — la mayoria de las {@code PLACA_RECT} no lo traen
 * @param lines texto de la placa, en el orden en que se lee de arriba a abajo
 */
public record SignPlate(SignTemplate template, String code, List<SignLine> lines) {

    public SignPlate {
        Objects.requireNonNull(template, "template");
        lines = List.copyOf(lines);
    }
}
