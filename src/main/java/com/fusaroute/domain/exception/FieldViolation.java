package com.fusaroute.domain.exception;

/**
 * Un incumplimiento de validacion sobre un campo concreto del registro. Se
 * acumulan varios para devolverlos juntos y que el formulario los muestre todos
 * de una vez, no de uno en uno.
 */
public record FieldViolation(String field, String message) {
}
