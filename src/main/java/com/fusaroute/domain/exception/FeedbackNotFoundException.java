package com.fusaroute.domain.exception;

/**
 * El comentario no existe, o existe y es de otro usuario (RF-08, SCRUM-94 y SCRUM-95).
 *
 * Los dos casos comparten excepcion y terminan en 404 a proposito. Un 403 para el
 * comentario ajeno seria mas descriptivo, pero confirmaria que ese id existe: con
 * eso cualquiera puede recorrer ids y contar cuantos comentarios tiene el sistema y
 * en que rango viven. El 404 no distingue "no existe" de "no es tuyo", que es
 * exactamente lo que se quiere hacia afuera. Mismo criterio que
 * {@link RouteNotFoundException}, donde "no existe" y "esta suspendida" tambien
 * responden igual.
 *
 * Esto no debilita la regla de autorizacion: el administrador si tiene su propio
 * camino ({@code /api/admin/comments}), protegido por rol en Spring Security.
 */
public class FeedbackNotFoundException extends RuntimeException {

    public FeedbackNotFoundException(String message) {
        super(message);
    }
}
