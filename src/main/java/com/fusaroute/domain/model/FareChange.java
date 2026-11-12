package com.fusaroute.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Que hacer con las tarifas al modificar una ruta. Un cambio de tarifa es una fila nueva
 * con {@code validFrom = hoy}, no un UPDATE: asi la fecha de "ultima actualizacion" que ve
 * el pasajero es la real. Si el conjunto vigente (monto + punto) no cambio, no se toca nada.
 *
 * @param toWrite filas con {@code validFrom = hoy}; si ya existe una de hoy para ese punto, se actualiza
 * @param removedPoints puntos vigentes que ya no estan en la tarifa nueva (puede incluir {@code null}):
 *     no hay forma de "cerrar" una vigencia, asi que sus filas se eliminan
 */
public record FareChange(boolean changed, List<Fare> toWrite, List<String> removedPoints) {

    public static FareChange between(List<Fare> currentFares, List<FareDraft> requested, LocalDate today) {
        Map<String, BigDecimal> current = new HashMap<>();
        currentFares.forEach(f -> current.put(f.referencePoint(), f.amount()));
        Map<String, BigDecimal> wanted = new HashMap<>();
        requested.forEach(f -> wanted.put(clean(f.referencePoint()), f.amount()));

        boolean same = current.keySet().equals(wanted.keySet())
                && current.entrySet().stream().allMatch(e -> e.getValue().compareTo(wanted.get(e.getKey())) == 0);
        if (same) {
            return new FareChange(false, List.of(), List.of());
        }
        List<String> removed = new ArrayList<>();
        current.keySet().stream().filter(p -> !wanted.containsKey(p)).forEach(removed::add);
        return new FareChange(true, toRows(requested, today), removed);
    }

    /** Filas nuevas ordenadas por monto ascendente: asi se asigna {@code order_index}. */
    public static List<Fare> toRows(List<FareDraft> requested, LocalDate today) {
        List<FareDraft> sorted = requested.stream().sorted(Comparator.comparing(FareDraft::amount)).toList();
        List<Fare> rows = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            rows.add(new Fare(clean(sorted.get(i).referencePoint()), sorted.get(i).amount(), i, today));
        }
        return rows;
    }

    private static String clean(String point) {
        return point == null ? null : point.strip();
    }
}
