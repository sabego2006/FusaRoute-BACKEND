package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.Ride;

import java.util.List;

public interface RideRepositoryPort {

    /**
     * Guarda el viaje y, en la MISMA transaccion, borra los del usuario que queden
     * fuera de los {@code keep} mas recientes, para que nunca quede un historial de 11.
     */
    Ride saveKeepingLatest(Ride ride, int keep);

    List<Ride> findRecentByUser(Long userId, int limit);
}
