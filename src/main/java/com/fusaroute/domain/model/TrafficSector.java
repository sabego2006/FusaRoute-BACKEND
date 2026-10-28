package com.fusaroute.domain.model;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Sector de la ciudad que se congestiona en una franja horaria conocida (RF-09).
 *
 * El trancon es del <strong>sector, no de la ruta</strong>: si la Carrera 1 se tranca a
 * las 5 de la tarde, lo sufre cualquier buseta que pase por ahi. Por eso el sector se
 * registra una sola vez con los barrios que abarca, en vez de repetir la misma ventana
 * en cada ruta — y por eso el aviso puede aparecer incluso en la mejor ruta disponible:
 * muchas veces atravesar el sector es inevitable.
 *
 * La ventana son dos horas y no un booleano a proposito: el aviso se enciende 30 minutos
 * antes de {@code startTime} y se apaga al llegar a {@code endTime}, un calculo que un
 * booleano no puede sostener.
 *
 * Sin distincion por dia de la semana este semestre: la misma ventana rige todos los
 * dias. Es una limitacion de alcance declarada, no un olvido.
 *
 * @param neighborhoods barrios que quedan dentro del sector
 * @param startTime hora en que historicamente arranca la congestion
 * @param endTime hora en que afloja; el aviso se apaga al llegar aqui
 */
public record TrafficSector(Long id, String name, Set<String> neighborhoods,
                            LocalTime startTime, LocalTime endTime) {

    /** Cuanto antes de la hora de inicio empieza a avisarse. */
    public static final Duration ADVANCE_NOTICE = Duration.ofMinutes(30);

    public TrafficSector {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        neighborhoods = Set.copyOf(neighborhoods);
    }

    /**
     * Hora a partir de la cual se avisa: media hora antes de que empiece el trancon,
     * para que al usuario le de tiempo de decidir.
     */
    public LocalTime warningStartsAt() {
        // LocalTime.minus da la vuelta al reloj solo, asi que una ventana de 00:10
        // empieza a avisarse a las 23:40 del dia anterior sin calculo extra.
        return startTime.minus(ADVANCE_NOTICE);
    }

    /** Si a {@code now} hay que mostrar el aviso de este sector. */
    public boolean isWarningActiveAt(LocalTime now) {
        LocalTime from = warningStartsAt();
        if (from.isBefore(endTime)) {
            return !now.isBefore(from) && now.isBefore(endTime);
        }
        // El intervalo de aviso cruza la medianoche: son dos pedazos de reloj.
        return !now.isBefore(from) || now.isBefore(endTime);
    }

    /**
     * Si un recorrido pasa por este sector. Basta con que toque uno de sus barrios: el
     * trancon afecta a quien lo atraviesa, sin importar por cual de sus calles entre.
     */
    public boolean covers(Collection<String> traversedNeighborhoods) {
        return traversedNeighborhoods.stream().anyMatch(neighborhoods::contains);
    }
}
