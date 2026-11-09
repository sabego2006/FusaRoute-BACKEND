package com.fusaroute.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Lugar canonico con coordenada (P1, SCRUM-8): un sitio que el usuario puede elegir como origen o
 * destino. Reemplaza a los nombres sueltos de {@code route_neighborhoods} como punto de partida de la
 * busqueda: un lugar tiene UNA coordenada y UN nombre sin duplicados, y los nombres con que la gente
 * lo llama ({@code aliases}) se resuelven a el.
 *
 * @param slug identificador estable en kebab-case ({@code monumento-el-indio})
 * @param name nombre que ve el usuario
 * @param aliases otras formas de escribirlo; no incluye el nombre
 */
public record Place(Long id, String slug, String name, Coordinate coordinate, List<String> aliases) {

    public Place {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(coordinate, "coordinate");
        aliases = List.copyOf(aliases);
    }

    /**
     * Si {@code term} es el nombre o un alias de este lugar, comparando
     * {@linkplain PlaceName#normalize normalizado} (sin tildes, mayusculas ni puntos). Es la regla de la
     * busqueda legada por nombre: {@code originNeighborhood=Monumento El Indio} resuelve al mismo lugar
     * que {@code Indio}.
     */
    public boolean matchesName(String term) {
        String needle = PlaceName.normalize(term);
        if (needle.isEmpty()) {
            return false;
        }
        return PlaceName.normalize(name).equals(needle)
                || aliases.stream().anyMatch(alias -> PlaceName.normalize(alias).equals(needle));
    }

    /** Nombre y alias, para buscar el lugar por texto en el recorrido de una ruta sin trazado. */
    public List<String> allNames() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(name), aliases.stream()).toList();
    }
}
