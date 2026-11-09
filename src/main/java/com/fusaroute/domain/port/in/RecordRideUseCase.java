package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.Ride;

/** Guarda el viaje "Ya me subi" y deja el historial en los 10 mas recientes. */
public interface RecordRideUseCase {

    Ride record(RecordRideCommand command);
}
