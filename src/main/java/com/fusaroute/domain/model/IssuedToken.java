package com.fusaroute.domain.model;

import java.time.Instant;

/**
 * Token de acceso ya firmado, junto con el instante en que vence. El dominio no
 * sabe que por dentro es un JWT: solo transporta el valor opaco y su caducidad.
 */
public record IssuedToken(String value, Instant expiresAt) {
}
