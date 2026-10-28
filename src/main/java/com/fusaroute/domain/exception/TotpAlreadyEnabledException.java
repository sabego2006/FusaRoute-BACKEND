package com.fusaroute.domain.exception;

/**
 * El usuario pidio activar TOTP cuando ya lo tiene activo (SCRUM-179). El flujo
 * de re-enrolamiento es explicito: primero {@code /enroll} genera un secreto
 * nuevo (reemplazando el anterior) y luego {@code /confirm} lo activa; esta
 * excepcion no deberia dispararse en el flujo normal, pero existe para blindar
 * el caso de uso contra una peticion incoherente que termine en un 500.
 */
public class TotpAlreadyEnabledException extends RuntimeException {

    public TotpAlreadyEnabledException() {
        super("El segundo factor ya esta activo");
    }
}
