package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.CongestionWarning;
import com.fusaroute.domain.model.FavoriteDestination;

import java.time.Instant;
import java.util.List;

/**
 * El destino favorito tal como lo ve el cliente (RF-10, SCRUM-21).
 *
 * Lleva el aviso de congestion dentro a proposito. El criterio de aceptacion pide que
 * al abrir la busqueda se vea de una vez el aviso de tranco habitual del favorito
 * "sin necesidad de iniciar una busqueda manualmente"; si el aviso viajara aparte, el
 * frontend tendria que encadenar dos peticiones antes de poder pintar la pantalla.
 *
 * Los sectores se calculan sobre el barrio del favorito, que es lo unico que se puede
 * saber antes de una busqueda: todavia no hay origen, asi que todavia no hay ruta.
 *
 * No lleva {@code userId}: el endpoint ya es "mi favorito".
 *
 * @param destination barrio favorito, listo para precargar el campo de destino
 * @param savedAt cuando se marco
 * @param congestionSectors sectores del barrio en su franja de trancon ahora mismo;
 *     lista vacia si ninguno lo esta
 */
public record FavoriteDestinationResponse(String destination,
                                          Instant savedAt,
                                          List<CongestionSectorResponse> congestionSectors) {

    static FavoriteDestinationResponse from(FavoriteDestination favorite, CongestionWarning congestion) {
        return new FavoriteDestinationResponse(favorite.destination(), favorite.savedAt(),
                CongestionSectorResponse.from(congestion));
    }
}
