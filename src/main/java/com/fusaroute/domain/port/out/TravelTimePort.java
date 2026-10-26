package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.GeoCoordinate;
import java.time.Duration;

public interface TravelTimePort {

    Duration estimateTravelTime(GeoCoordinate origin, GeoCoordinate destination);
}
