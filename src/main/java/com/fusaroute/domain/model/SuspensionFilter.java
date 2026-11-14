package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.InvalidSuspensionException;

import java.util.List;
import java.util.Locale;

/** Filtro {@code state} del listado de suspensiones del administrador. */
public enum SuspensionFilter {
    ACTIVE, UPCOMING, PAST, ALL;

    /** Sin valor es {@link #ALL}; un valor desconocido es un error del cliente (400), no un listado vacio. */
    public static SuspensionFilter parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidSuspensionException(List.of(
                    new FieldViolation("state", "Debe ser active, upcoming, past o all")));
        }
    }

    /** {@code PAST} agrupa las terminadas y las levantadas. */
    public boolean accepts(SuspensionState state) {
        return switch (this) {
            case ALL -> true;
            case ACTIVE -> state == SuspensionState.ACTIVE;
            case UPCOMING -> state == SuspensionState.UPCOMING;
            case PAST -> state == SuspensionState.ENDED || state == SuspensionState.LIFTED;
        };
    }
}
