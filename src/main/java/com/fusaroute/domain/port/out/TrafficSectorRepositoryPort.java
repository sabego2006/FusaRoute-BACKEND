package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.TrafficSector;

import java.util.List;

/**
 * Acceso a los sectores de la ciudad con congestion historica registrada (RF-09).
 */
public interface TrafficSectorRepositoryPort {

    /**
     * Todos los sectores registrados. Se traen todos y el filtrado por hora y por
     * recorrido lo hace el dominio: son una decena de filas que cambian muy poco, y
     * tenerlas completas deja la regla de los 30 minutos en un solo lugar en vez de
     * repartida entre una consulta SQL y el codigo.
     */
    List<TrafficSector> findAll();
}
