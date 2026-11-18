package com.fusaroute.domain.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    /**
     * Lugar del catalogo mas cercano a {@code point}, si queda a menos de {@code maxMeters}
     * (SCRUM-198). Es la "referencia" con que se le explica a alguien una caminata: un nombre que
     * reconoce, en vez de una coordenada o un rumbo suelto.
     *
     * <p>El radio es parte de la pregunta y no un detalle: nombrar el lugar mas cercano sin mirar a
     * que distancia esta manda a la persona a otra esquina cuando el catalogo no cubre ese sector.
     *
     * @return vacio si {@code places} esta vacio o si el mas cercano queda mas lejos de
     *     {@code maxMeters}
     */
    public static Optional<Place> nearestTo(List<Place> places, Coordinate point, double maxMeters) {
        if (point == null) {
            return Optional.empty();
        }
        return places.stream()
                .min(Comparator.comparingDouble(place -> place.coordinate.distanceInMetersTo(point)))
                .filter(place -> place.coordinate.distanceInMetersTo(point) <= maxMeters);
    }

    /** Nombre y alias, para buscar el lugar por texto en el recorrido de una ruta sin trazado. */
    public List<String> allNames() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(name), aliases.stream()).toList();
    }
}
