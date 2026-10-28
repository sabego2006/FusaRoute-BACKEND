package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.Coordinate;

import java.util.List;

/**
 * Trazado de una ruta como LineString de GeoJSON, que es el formato que consumen
 * directamente las librerias de mapas (el frontend se lo pasa tal cual a
 * {@code map.data.addGeoJson}).
 *
 * Ojo con el orden: GeoJSON escribe <strong>[longitud, latitud]</strong>, al reves de
 * como se nombran las coordenadas al hablar. El dominio las guarda como (lat, lng), asi
 * que aqui se invierten de vuelta — es el mismo cruce que hace
 * {@code RoutePersistenceMapper} al leerlas de la base, en sentido contrario.
 */
public record GeoJsonLineStringResponse(String type, List<List<Double>> coordinates) {

    private static final String LINE_STRING = "LineString";

    /**
     * @return {@code null} si la ruta todavia no tiene trazado cargado. Se prefiere null
     *     a un LineString vacio: un LineString sin puntos no es GeoJSON valido, y el
     *     cliente tiene que poder distinguir "no hay dato" de "hay una linea vacia".
     */
    static GeoJsonLineStringResponse from(List<Coordinate> path) {
        if (path.isEmpty()) {
            return null;
        }
        return new GeoJsonLineStringResponse(LINE_STRING, path.stream()
                .map(point -> List.of(point.longitude(), point.latitude()))
                .toList());
    }
}
