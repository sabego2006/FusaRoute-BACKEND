package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.EmailAlreadyRegisteredException;
import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.InvalidCredentialsException;
import com.fusaroute.domain.exception.InvalidRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Manejo central de errores. Usa {@link ProblemDetail} (RFC 9457, nativo de
 * Spring 6) para que el cuerpo de error sea uniforme en toda la API.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Validacion de negocio: devuelve todos los incumplimientos juntos. */
    @ExceptionHandler(InvalidRegistrationException.class)
    public ProblemDetail handleInvalidRegistration(InvalidRegistrationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        List<FieldViolation> errors = e.getViolations();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Login rechazado: 401 con el mensaje unico, sin distinguir el motivo. */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /** JSON malformado o ilegible: 400 con mensaje generico, sin filtrar detalles. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "El cuerpo de la peticion no es valido");
    }

    /**
     * Ruta inexistente: 404. Sin este manejador la red de seguridad de abajo la
     * convertia en un 500. Solo se ve estando autenticado, porque sin token el
     * filtro de seguridad responde 401 antes de llegar aqui.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNotFound(NoResourceFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "El recurso solicitado no existe");
    }

    /** Ruta que existe pero con otro metodo HTTP: 405 en vez de un 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED, "Metodo HTTP no permitido en este recurso");
    }

    /**
     * Red de seguridad: cualquier fallo no previsto es un 500 con mensaje
     * generico. La traza se loguea del lado del servidor, nunca se devuelve al
     * cliente.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Error no controlado", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrio un error inesperado");
    }
}
