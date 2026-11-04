package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.InvalidFavoriteDestinationException;

import java.time.Instant;

/**
 * El destino favorito de un usuario (RF-10, SCRUM-21).
 *
 * Es uno solo por cuenta, no una lista: el criterio de aceptacion dice que marcar uno
 * nuevo reemplaza al anterior. Eso no se sostiene solo aqui — la tabla usa
 * {@code user_id} como llave primaria, asi que la base tampoco deja tener dos.
 *
 * Vive en la cuenta y no en el dispositivo, igual que el historial de RF-07: el
 * usuario debe encontrar su favorito al entrar desde otro telefono.
 *
 * Guarda el nombre del barrio, no una coordenada: es lo que se escribe en el campo
 * destino de la busqueda, que es exactamente lo que hay que precargar.
 *
 * @param userId      dueno del favorito; nunca se comparte entre cuentas.
 * @param destination barrio de destino tal como se eligio.
 * @param savedAt     cuando se marco. No ordena nada —solo hay uno— pero deja ver
 *                    desde cuando esta puesto.
 */
public record FavoriteDestination(Long userId, String destination, Instant savedAt) {

    /** Mismo ancho que la columna y que search_history.destination. */
    public static final int MAX_DESTINATION_LENGTH = 120;

    public FavoriteDestination {
        if (userId == null) {
            throw new InvalidFavoriteDestinationException("El favorito siempre pertenece a un usuario");
        }
        destination = requireDestination(destination);
        if (savedAt == null) {
            throw new InvalidFavoriteDestinationException("El favorito necesita una fecha");
        }
    }

    private static String requireDestination(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidFavoriteDestinationException("El destino favorito no puede ir vacio");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_DESTINATION_LENGTH) {
            // Se rechaza en vez de recortar: un nombre cortado a la mitad no coincide
            // con ningun barrio del catalogo y la precarga quedaria inservible.
            throw new InvalidFavoriteDestinationException(
                    "El destino favorito no puede pasar de " + MAX_DESTINATION_LENGTH + " caracteres");
        }
        return trimmed;
    }
}
