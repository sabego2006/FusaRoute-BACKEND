package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.Ride;

import java.util.List;

/** Los 10 viajes mas recientes del usuario, del mas nuevo al mas viejo. */
public interface GetRideHistoryUseCase {

    List<Ride> ridesOf(Long userId);
}
