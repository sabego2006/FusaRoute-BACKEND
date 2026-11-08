package com.fusaroute.domain.port.in;

/**
 * Cuantas respuestas nuevas tiene el usuario desde su ingreso anterior
 * (RF-14, SCRUM-101).
 *
 * Es el reemplazo declarado de la notificacion push, que esta fuera de alcance: no
 * hay aviso proactivo, sino un chequeo del backend que el frontend hace al cargar la
 * sesion y pinta como contador en el icono de comentarios.
 */
public interface CountUnreadRepliesUseCase {

    /**
     * Comentarios del usuario respondidos despues de su INGRESO ANTERIOR, no del
     * actual.
     *
     * La distincion es la que hace que el contador sirva para algo. El login exitoso
     * mueve {@code last_login_at} a "ahora", asi que comparar contra esa columna
     * daria cero siempre que el frontend consulte justo despues de iniciar sesion,
     * que es exactamente cuando consulta. Por eso el usuario guarda tambien
     * {@code previous_login_at} y la cuenta se hace contra ese instante.
     *
     * Un usuario que nunca habia entrado antes (primer login) no tiene ingreso
     * anterior: se devuelve 0 en vez de contar todo su historial, porque no hay nada
     * que haya pasado "desde la ultima vez".
     */
    long unreadReplies(Long userId);
}
