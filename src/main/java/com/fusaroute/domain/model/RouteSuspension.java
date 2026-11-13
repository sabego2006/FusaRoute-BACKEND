package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.InvalidSuspensionException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Ventana de suspension temporal de una ruta (RF-12, SCRUM-25). Es distinta del soft delete
 * ({@link RouteStatus#INACTIVA}): la ruta sigue existiendo y vuelve sola cuando la ventana se cierra.
 *
 * <p>La ventana es semiabierta, {@code [startsAt, endsAt)}: justo en {@code startsAt} ya esta vigente y
 * justo en {@code endsAt} ya no. Esa es la unica definicion de "vigente" del sistema; la base la aplica en
 * una sola consulta y el dominio la repite aqui para poder probarla con un reloj fijo.
 *
 * @param id         nulo antes de guardarla
 * @param routeId    la ruta suspendida
 * @param routeName  nombre de la ruta, solo para mostrar
 * @param reason     motivo opcional (maximo {@value #MAX_REASON_LENGTH} caracteres, sin enlaces); nulo si no hay
 * @param liftedAt   cuando el administrador la reactivo a mano; nulo si no se levanto
 */
public record RouteSuspension(Long id, Long routeId, String routeName, Instant startsAt, Instant endsAt,
                              String reason, Instant liftedAt) {

    public static final int MAX_REASON_LENGTH = 200;

    /** Tope de duracion: caza el error tipico de escribir el anio equivocado. */
    public static final Duration MAX_DURATION = Duration.ofDays(90);

    /** Mismo criterio que el filtro de enlaces de los comentarios (ver {@code Feedback}). */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://|www\\.|\\b[a-z0-9][a-z0-9-]*\\."
                    + "(com|co|net|org|edu|gov|io|app|info|biz|ly|gl|xyz|online|site|link)\\b)",
            Pattern.CASE_INSENSITIVE);

    public RouteSuspension {
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt debe ser posterior a startsAt");
        }
    }

    /**
     * Abre una suspension nueva, validada contra {@code now}.
     *
     * @throws InvalidSuspensionException con TODAS las violaciones juntas, por campo
     */
    public static RouteSuspension open(Long routeId, String routeName, Instant startsAt, Instant endsAt,
                                       String reason, Instant now) {
        List<FieldViolation> violations = violations(startsAt, endsAt, reason, now);
        if (!violations.isEmpty()) {
            throw new InvalidSuspensionException(violations);
        }
        return new RouteSuspension(null, routeId, routeName, startsAt, endsAt, normalizeReason(reason), null);
    }

    /** Las reglas de la ventana y del motivo, sin lanzar nada: el caso de uso las devuelve todas juntas. */
    public static List<FieldViolation> violations(Instant startsAt, Instant endsAt, String reason, Instant now) {
        List<FieldViolation> violations = new ArrayList<>();
        if (startsAt == null) {
            violations.add(new FieldViolation("startsAt", "La fecha de inicio es obligatoria"));
        }
        if (endsAt == null) {
            violations.add(new FieldViolation("endsAt", "La fecha de fin es obligatoria"));
        }
        if (startsAt != null && endsAt != null) {
            if (!endsAt.isAfter(startsAt)) {
                violations.add(new FieldViolation("endsAt", "El fin debe ser posterior al inicio"));
            } else if (Duration.between(startsAt, endsAt).compareTo(MAX_DURATION) > 0) {
                violations.add(new FieldViolation("endsAt",
                        "La suspension no puede durar mas de " + MAX_DURATION.toDays() + " dias"));
            }
        }
        if (endsAt != null && !endsAt.isAfter(now)) {
            violations.add(new FieldViolation("endsAt", "El fin debe estar en el futuro"));
        }
        String cleaned = normalizeReason(reason);
        if (cleaned != null) {
            if (cleaned.length() > MAX_REASON_LENGTH) {
                violations.add(new FieldViolation("reason",
                        "El motivo no puede pasar de " + MAX_REASON_LENGTH + " caracteres"));
            } else if (URL_PATTERN.matcher(cleaned).find()) {
                violations.add(new FieldViolation("reason", "El motivo no puede incluir enlaces"));
            }
        }
        return violations;
    }

    /** Sin espacios en los bordes; vacio equivale a "sin motivo". */
    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.strip();
    }

    /** Vigente en {@code now}: ya empezo, no ha terminado y nadie la levanto. */
    public boolean isEffectiveAt(Instant now) {
        return liftedAt == null && !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    /**
     * Estado en {@code now}, con esta precedencia: levantada, terminada, vigente, proxima. Una suspension
     * levantada antes de empezar es {@code LIFTED}, no {@code UPCOMING}.
     */
    public SuspensionState stateAt(Instant now) {
        if (liftedAt != null) {
            return SuspensionState.LIFTED;
        }
        if (!now.isBefore(endsAt)) {
            return SuspensionState.ENDED;
        }
        return now.isBefore(startsAt) ? SuspensionState.UPCOMING : SuspensionState.ACTIVE;
    }

    /** Se puede reactivar a mano mientras no haya terminado ni se haya levantado (una proxima tambien). */
    public boolean canBeLiftedAt(Instant now) {
        return liftedAt == null && now.isBefore(endsAt);
    }

    /**
     * Si esta ventana se solapa con {@code [otherStart, otherEnd)}. Ventanas contiguas (el fin de una es el
     * inicio de la otra) NO se solapan.
     */
    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return startsAt.isBefore(otherEnd) && otherStart.isBefore(endsAt);
    }

    /** Copia ya levantada en {@code at}. */
    public RouteSuspension lifted(Instant at) {
        return new RouteSuspension(id, routeId, routeName, startsAt, endsAt, reason, at);
    }
}
