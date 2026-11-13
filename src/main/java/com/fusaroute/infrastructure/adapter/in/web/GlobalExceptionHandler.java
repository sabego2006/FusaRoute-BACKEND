package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.AccountLockedException;
import com.fusaroute.domain.exception.EmailAlreadyRegisteredException;
import com.fusaroute.domain.exception.FeedbackAlreadyAnsweredException;
import com.fusaroute.domain.exception.FeedbackContainsUrlException;
import com.fusaroute.domain.exception.FeedbackForbiddenWordException;
import com.fusaroute.domain.exception.FeedbackLengthException;
import com.fusaroute.domain.exception.FeedbackNotFoundException;
import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.IncorrectCurrentPasswordException;
import com.fusaroute.domain.exception.InvalidCoordinateException;
import com.fusaroute.domain.exception.InvalidCredentialsException;
import com.fusaroute.domain.exception.InvalidFavoriteDestinationException;
import com.fusaroute.domain.exception.InvalidRideException;
import com.fusaroute.domain.exception.InvalidFeedbackPageException;
import com.fusaroute.domain.exception.InvalidFeedbackReplyException;
import com.fusaroute.domain.exception.InvalidMonitoringQueryException;
import com.fusaroute.domain.exception.InvalidProfileException;
import com.fusaroute.domain.exception.InvalidRegistrationException;
import com.fusaroute.domain.exception.InvalidRouteException;
import com.fusaroute.domain.exception.InvalidTotpException;
import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.exception.RouteConflictException;
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

    /** Validacion de negocio al crear o modificar una ruta (RF-11): misma estructura por campo que el registro. */
    @ExceptionHandler(InvalidRouteException.class)
    public ProblemDetail handleInvalidRoute(InvalidRouteException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("errors", e.getViolations());
        return problem;
    }

    /** Nombre o letrero de ruta repetido, o restaurar una ruta que no estaba inactiva (RF-11): 409. */
    @ExceptionHandler(RouteConflictException.class)
    public ProblemDetail handleRouteConflict(RouteConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
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

    /**
     * Las tres reglas del texto del comentario (RF-08, SCRUM-91) comparten handler
     * porque comparten destino: 400 con el mensaje tal cual, que es lo que el
     * frontend pinta bajo el campo. Siguen siendo tres excepciones distintas en el
     * dominio —ahi es donde la distincion importa, al probar cada regla por separado—
     * y agruparlas aqui no la pierde: si manana una necesita otro codigo o un
     * {@code error} machine-readable, se saca de esta lista.
     *
     * Van sin lista de {@code errors} por campo, igual que el destino favorito: el
     * cuerpo tiene un solo campo y no hay nada que repartir.
     */
    @ExceptionHandler({
            FeedbackLengthException.class,
            FeedbackContainsUrlException.class,
            FeedbackForbiddenWordException.class,
            InvalidFeedbackReplyException.class,
            InvalidFeedbackPageException.class})
    public ProblemDetail handleInvalidFeedback(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * El comentario no existe, o existe y es de otro usuario (SCRUM-94 y SCRUM-95).
     * Los dos casos responden 404; el por que esta en el javadoc de
     * {@link FeedbackNotFoundException}.
     */
    @ExceptionHandler(FeedbackNotFoundException.class)
    public ProblemDetail handleFeedbackNotFound(FeedbackNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * Se quiso editar o borrar un comentario ya respondido (SCRUM-94 y SCRUM-95).
     * 409 y no 403: el permiso esta bien, lo que choca es el estado del recurso. El
     * frontend debe recargar el comentario para mostrar la respuesta que aparecio,
     * no mandar al usuario a iniciar sesion.
     */
    @ExceptionHandler(FeedbackAlreadyAnsweredException.class)
    public ProblemDetail handleFeedbackAlreadyAnswered(FeedbackAlreadyAnsweredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
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

    /** Datos del viaje "Ya me subi" invalidos: 400 con el mensaje del dominio. */
    @ExceptionHandler(InvalidRideException.class)
    public ProblemDetail handleInvalidRide(InvalidRideException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** La ruta no existe o esta suspendida: para el catalogo publico es lo mismo. */
    @ExceptionHandler(RouteNotFoundException.class)
    public ProblemDetail handleRouteNotFound(RouteNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** Ninguna ruta candidata conecta el origen con el destino solicitados (RF-04). */
    @ExceptionHandler(NoRouteAvailableException.class)
    public ProblemDetail handleNoRouteAvailable(NoRouteAvailableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        // P1: la ruta con trazado mas cercana a cada punta, para decirle al usuario cuanto le falta.
        java.util.Map<String, Object> nearest = new java.util.LinkedHashMap<>();
        if (e.nearestToOrigin() != null) {
            nearest.put("origin", java.util.Map.of("routeName", e.nearestToOrigin().routeName(),
                    "distanceInMeters", e.nearestToOrigin().distanceInMeters()));
        }
        if (e.nearestToDestination() != null) {
            nearest.put("destination", java.util.Map.of("routeName", e.nearestToDestination().routeName(),
                    "distanceInMeters", e.nearestToDestination().distanceInMeters()));
        }
        if (!nearest.isEmpty()) {
            problem.setProperty("nearestRoutes", nearest);
        }
        return problem;
    }

    /** Una punta de la busqueda no se pudo resolver (P1): 400, y dice cual de las dos. */
    @ExceptionHandler(com.fusaroute.domain.exception.InvalidSearchPointException.class)
    public ProblemDetail handleInvalidSearchPoint(com.fusaroute.domain.exception.InvalidSearchPointException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("point", e.side());
        return problem;
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

    /** limit fuera de 1..500 en las consultas de monitoreo del administrador (SCRUM-186). */
    @ExceptionHandler(InvalidMonitoringQueryException.class)
    public ProblemDetail handleInvalidMonitoringQuery(InvalidMonitoringQueryException e) {
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
