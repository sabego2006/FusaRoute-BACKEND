package com.fusaroute.domain.model;

/** Como termino una busqueda de ruta, para medir cuanto del uso acaba en una ruta util. */
public enum SearchOutcome {
    /** Hubo ruta y la caminata es razonable. */
    FOUND,
    /** Hubo ruta, pero el usuario tiene que caminar mucho para llegar a ella. */
    LONG_WALK,
    /** Ninguna ruta conecta origen y destino. */
    NOT_FOUND,
    /** La busqueda fallo por un error nuestro o de un proveedor externo. */
    ERROR
}
