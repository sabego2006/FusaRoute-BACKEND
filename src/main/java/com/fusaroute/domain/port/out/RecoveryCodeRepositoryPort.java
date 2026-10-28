package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.RecoveryCode;

import java.util.List;

/**
 * Puerto de salida hacia el almacen de codigos de respaldo (SCRUM-179).
 *
 * Es un puerto separado del repositorio de usuarios porque los respaldos tienen
 * su propio ciclo (varias filas por usuario, consumo individual) y porque
 * aislarlo mantiene a {@link UserRepositoryPort} con una sola responsabilidad.
 */
public interface RecoveryCodeRepositoryPort {

    /**
     * Reemplaza TODOS los codigos de respaldo del usuario por el conjunto nuevo.
     * Se usa al confirmar un enrolamiento: el conjunto anterior deja de servir.
     */
    void replaceAllForUser(Long userId, List<RecoveryCode> newCodes);

    /** Devuelve solo los codigos aun no consumidos del usuario. */
    List<RecoveryCode> findActiveByUser(Long userId);

    /** Marca el codigo como consumido. */
    void markUsed(RecoveryCode code);

    /** Borra todos los codigos del usuario (apagado del segundo factor). */
    void deleteAllForUser(Long userId);
}
