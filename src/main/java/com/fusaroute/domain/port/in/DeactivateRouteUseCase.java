package com.fusaroute.domain.port.in;

/** "Elimina" una ruta pasandola a INACTIVA: soft delete, no se borra ninguna fila (RF-11). */
public interface DeactivateRouteUseCase {

    void deactivate(Long id);
}
