package com.fusaroute.domain.model;

/**
 * La ruta con trazado mas cercana a una punta de la busqueda, para el 404 "no hay ruta": le dice al
 * usuario cuanto le falta (caminata aproximada, ya con el factor de rodeo) para alcanzar el servicio.
 */
public record NearestRoute(String routeName, long distanceInMeters) {
}
