package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.InvalidSuspensionException;
import com.fusaroute.domain.port.in.CreateRouteSuspensionCommand;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuerpo de {@code POST /api/admin/routes/{id}/suspensions}. Las fechas llegan como texto y se parsean aqui,
 * y no con el deserializador de Jackson, para que una fecha mal escrita (o sin offset) se reporte POR CAMPO
 * como el resto de validaciones, en vez del 400 generico de "cuerpo no valido".
 */
public record CreateSuspensionRequest(String startsAt, String endsAt, String reason) {

    CreateRouteSuspensionCommand toCommand(Long routeId, Long adminId) {
        List<FieldViolation> formatErrors = new ArrayList<>();
        Instant start = parse("startsAt", startsAt, formatErrors);
        Instant end = parse("endsAt", endsAt, formatErrors);
        if (!formatErrors.isEmpty()) {
            throw new InvalidSuspensionException(formatErrors);
        }
        return new CreateRouteSuspensionCommand(routeId, start, end, reason, adminId);
    }

    /** Vacio o ausente es nulo (el dominio lo reporta como obligatorio); ISO-8601 sin offset es un error de formato. */
    private static Instant parse(String field, String value, List<FieldViolation> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException e) {
            errors.add(new FieldViolation(field, "Debe ser una fecha ISO-8601 con offset, p. ej. 2026-11-20T08:00:00-05:00"));
            return null;
        }
    }
}
