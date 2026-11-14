package com.fusaroute.domain.model;

/**
 * En que punto esta una suspension, visto desde un instante. Se calcula, no se guarda: lo unico que
 * se guarda son las dos fechas y, si el administrador la reactivo a mano, {@code liftedAt}.
 */
public enum SuspensionState {
    /** Ya empezo y no ha terminado ni se levanto: la ruta esta fuera de lo publico. */
    ACTIVE,
    /** Aun no empieza. */
    UPCOMING,
    /** Llego a su fin sin que nadie la levantara. */
    ENDED,
    /** El administrador reactivo la ruta a mano antes del fin (o antes del inicio). */
    LIFTED
}
