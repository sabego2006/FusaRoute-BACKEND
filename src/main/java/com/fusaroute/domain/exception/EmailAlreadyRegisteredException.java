package com.fusaroute.domain.exception;

/**
 * El correo ya tiene una cuenta. La lanza el caso de uso cuando
 * {@code existsByEmail} da verdadero, y tambien el adaptador de persistencia al
 * traducir el choque del UNIQUE de la base en la carrera de dos registros
 * simultaneos. En ambos casos termina en un 409, no en un 500.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("Ya existe una cuenta con ese correo");
    }
}
