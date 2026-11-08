package com.fusaroute.domain.port.out;

import java.util.Set;

/**
 * Banco de palabras prohibidas con el que se filtra un comentario (RF-08, SCRUM-91).
 *
 * Es puerto de salida y no una constante del dominio porque la lista es DATO y no
 * regla: cambia sin que cambie la logica, y debe poder revisarse en un PR sin
 * recompilar la clase que valida. El adaptador la lee de un archivo versionado en
 * {@code src/main/resources/moderation/forbidden-words.txt}.
 *
 * El puerto devuelve las palabras TAL COMO estan escritas en el archivo, sin
 * normalizar. Normalizar es la regla de comparacion ("cuando dos palabras son la
 * misma palabra") y eso vive en el dominio, en {@code Feedback}: si lo hiciera el
 * adaptador, la regla quedaria partida entre las dos capas y un adaptador distinto
 * podria normalizar diferente sin que ningun test del dominio lo notara.
 */
public interface ForbiddenWordsPort {

    /**
     * Las palabras del banco. Un conjunto vacio es valido y significa que no hay
     * filtro: el comentario solo tiene que cumplir longitud y ausencia de enlaces.
     */
    Set<String> words();
}
