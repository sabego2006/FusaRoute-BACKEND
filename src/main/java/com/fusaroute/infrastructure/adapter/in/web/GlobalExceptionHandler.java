package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.AccountLockedException;
import com.fusaroute.domain.exception.EmailAlreadyRegisteredException;
import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.IncorrectCurrentPasswordException;
import com.fusaroute.domain.exception.InvalidCoordinateException;
import com.fusaroute.domain.exception.InvalidCredentialsException;
import com.fusaroute.domain.exception.InvalidFavoriteDestinationException;
import com.fusaroute.domain.exception.InvalidProfileException;
import com.fusaroute.domain.exception.InvalidRegistrationException;
import com.fusaroute.domain.exception.InvalidTotpException;
import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.exception.TotpAlreadyEnabledException;
import com.fusaroute.domain.exception.TotpEnrollmentRequiredException;
import com.fusaroute.domain.exception.TotpNotEnabledException;
import com.fusaroute.domain.exception.TotpRequiredException;
import com.fusaroute.domain.exception.TravelTimeUnavailableException;
import com.fusaroute.domain.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Manejo central de errores. Extiende {@link ResponseEntityExceptionHandler} para
 * que Spring MVC delegue sus excepciones internas (415, 405, etc.) con el codigo
 * HTTP correcto en vez de caer al catch-all de 500. Usa {@link ProblemDetail}
 * (RFC 9457) para un cuerpo de error uniforme.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Validacion de negocio en registro: devuelve todos los incumplimientos juntos. */
    @ExceptionHandler(InvalidRegistrationException.class)
    public ProblemDetail handleInvalidRegistration(InvalidRegistrationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        List<FieldViolation> errors = e.getViolations();
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Validacion de negocio en perfil: misma estructura que el registro. */
    @ExceptionHandler(InvalidProfileException.class)
    public ProblemDetail handleInvalidProfile(InvalidProfileException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("errors", e.getViolations());
        return problem;
    }

    /**
     * Destino favorito invalido (RF-10). Va sin lista de {@code errors} porque el
     * cuerpo tiene un solo campo: no hay nada que repartir por campo y el mensaje
     * basta para pintarlo bajo el control.
     */
    @ExceptionHandler(InvalidFavoriteDestinationException.class)
    public ProblemDetail handleInvalidFavoriteDestination(InvalidFavoriteDestinationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
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

    /**
     * Cuenta bloqueada temporalmente tras el quinto intento fallido (SCRUM-155).
     * 423 Locked, distinto del 401 generico de {@link InvalidCredentialsException}:
     * el frontend debe mostrar un aviso diferente, no el de "credenciales
     * incorrectas". {@code error=ACCOUNT_LOCKED} es la pista machine-readable,
     * igual que en los handlers de TOTP; el {@code detail} no incluye el
     * instante de desbloqueo a proposito (ver {@link AccountLockedException}).
     */
    @ExceptionHandler(AccountLockedException.class)
    public ProblemDetail handleAccountLocked(AccountLockedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED, e.getMessage());
        problem.setProperty("error", "ACCOUNT_LOCKED");
        return problem;
    }

    /**
     * Login con contrasena correcta pero segundo factor requerido (SCRUM-179).
     * 401 con una PISTA machine-readable: el frontend lee {@code error} para
     * saber que debe pedir el codigo del segundo factor, sin tener que inferirlo
     * del texto del {@code detail}.
     */
    @ExceptionHandler(TotpRequiredException.class)
    public ProblemDetail handleTotpRequired(TotpRequiredException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        problem.setProperty("error", "TOTP_REQUIRED");
        return problem;
    }

    /**
     * Login con contrasena correcta pero el usuario aun no enrolo TOTP
     * (SCRUM-179). Es la rama de bootstrap: se devuelve el {@code enrollmentToken}
     * de vida corta para que el frontend arranque el flujo de enrolamiento
     * forzado.
     */
    @ExceptionHandler(TotpEnrollmentRequiredException.class)
    public ProblemDetail handleTotpEnrollmentRequired(TotpEnrollmentRequiredException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        problem.setProperty("error", "TOTP_ENROLLMENT_REQUIRED");
        problem.setProperty("enrollmentToken", e.getEnrollmentToken().value());
        problem.setProperty("expiresAt", e.getEnrollmentToken().expiresAt().toString());
        return problem;
    }

    /** Codigo del segundo factor incorrecto (SCRUM-179). */
    @ExceptionHandler(InvalidTotpException.class)
    public ProblemDetail handleInvalidTotp(InvalidTotpException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        problem.setProperty("error", "INVALID_TOTP");
        return problem;
    }

    /** TOTP ya estaba activo al pedir enrolamiento (SCRUM-179). */
    @ExceptionHandler(TotpAlreadyEnabledException.class)
    public ProblemDetail handleTotpAlreadyEnabled(TotpAlreadyEnabledException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Se pidio algo que exige el segundo factor activo y no lo esta (SCRUM-179). */
    @ExceptionHandler(TotpNotEnabledException.class)
    public ProblemDetail handleTotpNotEnabled(TotpNotEnabledException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** La contrasena actual es incorrecta al cambiarla. */
    @ExceptionHandler(IncorrectCurrentPasswordException.class)
    public ProblemDetail handleIncorrectPassword(IncorrectCurrentPasswordException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Usuario no encontrado. */
    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** La ruta no existe o esta suspendida: para el catalogo publico es lo mismo. */
    @ExceptionHandler(RouteNotFoundException.class)
    public ProblemDetail handleRouteNotFound(RouteNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** Ninguna ruta candidata conecta el origen con el destino solicitados (RF-04). */
    @ExceptionHandler(NoRouteAvailableException.class)
    public ProblemDetail handleNoRouteAvailable(NoRouteAvailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** Google Maps no respondio (RNF-01): 503, no 500, porque el fallo es del proveedor externo. */
    @ExceptionHandler(TravelTimeUnavailableException.class)
    public ProblemDetail handleTravelTimeUnavailable(TravelTimeUnavailableException e) {
        log.warn("Tiempo de viaje no disponible: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    /** Coordenada fuera de rango en la busqueda (RF-04): error del cliente, no un 500. */
    @ExceptionHandler(InvalidCoordinateException.class)
    public ProblemDetail handleInvalidCoordinate(InvalidCoordinateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Un id que no es numerico (/api/routes/abc) es un error del cliente, no un 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Parametro de la peticion no valido");
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

    // --- Sobrecargas de ResponseEntityExceptionHandler para ProblemDetail ---

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "El cuerpo de la peticion no es valido");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED, "Metodo HTTP no permitido en este recurso");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "El recurso solicitado no existe");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }
}
