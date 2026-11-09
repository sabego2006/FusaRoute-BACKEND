package com.fusaroute.domain.model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Ruta de buseta: un recorrido directo de un solo tramo, con sus barrios en orden
 * de recorrido y sus tarifas.
 *
 * Una ruta = un letrero = un sentido (SCRUM-184). El letrero que la buseta lleva en
 * el parabrisas identifica un recorrido en un sentido, asi que el recorrido de vuelta,
 * cuando se modele, es otra ruta con su propio letrero — nunca una columna "sentido"
 * sobre esta.
 */
public record Route(Long id, String name, String signFile, RouteType type, RouteStatus status,
                    List<String> neighborhoods, List<Fare> fares, List<Coordinate> path) {

    /** Tramo minimo en buseta para que una combinacion abordaje-bajada cuente como viaje. */
    private static final double MIN_RIDE_METERS = 50;

    /** Mismo formato que obliga ck_routes_sign_file en la base (V14). */
    private static final Pattern SIGN_FILE = Pattern.compile("^[A-Z0-9_]+$");

    public Route {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        // signFile null es valido: tres rutas del catalogo no tienen letrero fotografiado.
        if (signFile != null && !SIGN_FILE.matcher(signFile).matches()) {
            throw new IllegalArgumentException(
                    "signFile debe ser MAYUSCULAS, digitos y guion bajo: " + signFile);
        }
        neighborhoods = List.copyOf(neighborhoods);
        fares = List.copyOf(fares);
        path = List.copyOf(path);
    }

    /**
     * Indice del primer lugar del recorrido que coincide con {@code term}, pensado para
     * la punta de ORIGEN de una busqueda.
     *
     * <p>La coincidencia se decide en dos pasos, en este orden:
     *
     * <ol>
     *   <li>igualdad del nombre {@linkplain PlaceName#normalize normalizado};</li>
     *   <li>si ninguno iguala, que el termino este contenido en el nombre normalizado.</li>
     * </ol>
     *
     * <p>El orden importa y no es un detalle: varias rutas pasan por {@code 'Centro'} y
     * tambien por {@code 'Centro Comercial Manila'}. Sin la preferencia por la igualdad,
     * buscar "Centro" caeria en el centro comercial si ese aparece antes en la lista, y
     * el punto de abordaje saldria en otro barrio. Con ella, la contencion solo entra
     * cuando el usuario escribio algo que no es un lugar completo.
     *
     * @return vacio si {@code term} es nulo, vacio o no coincide con ningun lugar
     */
    public OptionalInt indexOfPlace(String term) {
        return indexOfPlace(term, false);
    }

    /**
     * Como {@link #indexOfPlace(String)} pero devuelve el ULTIMO lugar que coincide,
     * pensado para la punta de DESTINO.
     *
     * <p>Primero para el origen y ultimo para el destino es lo que hace que una ruta
     * circular siga siendo candidata: la busqueda exige que el origen vaya antes que el
     * destino en el recorrido, y un recorrido que vuelve a pasar por el mismo sector
     * repite nombres parecidos al principio y al final.
     */
    public OptionalInt lastIndexOfPlace(String term) {
        return indexOfPlace(term, true);
    }

    private OptionalInt indexOfPlace(String term, boolean last) {
        String needle = PlaceName.normalize(term);
        if (needle.isEmpty()) {
            return OptionalInt.empty();
        }
        OptionalInt exact = scan(needle, last, true);
        return exact.isPresent() ? exact : scan(needle, last, false);
    }

    private OptionalInt scan(String needle, boolean last, boolean exact) {
        int found = -1;
        for (int i = 0; i < neighborhoods.size(); i++) {
            String place = PlaceName.normalize(neighborhoods.get(i));
            boolean hit = exact ? place.equals(needle) : place.contains(needle);
            if (hit) {
                found = i;
                if (!last) {
                    break;
                }
            }
        }
        return found == -1 ? OptionalInt.empty() : OptionalInt.of(found);
    }

    public boolean isActive() {
        return status == RouteStatus.ACTIVA;
    }

    /** Si la ruta tiene trazado utilizable (al menos dos puntos). Sin el solo se puede buscar por nombre. */
    public boolean hasPath() {
        return path.size() >= 2;
    }

    /**
     * Busqueda por geometria (P1): la mejor forma de usar esta ruta para ir de {@code origin} a
     * {@code destination}. Un punto se proyecta sobre el trazado; la ruta sirve si existe una pasada por
     * el origen que quede ANTES que una pasada por el destino y ambas a menos de
     * {@code maxStraightMeters} (linea recta al trazado). Entre las combinaciones validas gana la de
     * menor caminata total.
     *
     * <p>Se consideran todas las pasadas y no solo la mas cercana: ver {@link PathProjection#passesWithin}.
     * Una ruta es un solo sentido, asi que nunca se ofrece el recorrido al reves.
     *
     * @return vacio si la ruta no tiene trazado o ninguna combinacion cumple
     */
    public Optional<RouteMatch> matchBetween(Coordinate origin, Coordinate destination, double maxStraightMeters) {
        List<BoardingPoint> boardings = PathProjection.passesWithin(path, origin, maxStraightMeters);
        if (boardings.isEmpty()) {
            return Optional.empty();
        }
        List<BoardingPoint> dropOffs = PathProjection.passesWithin(path, destination, maxStraightMeters);
        BoardingPoint bestBoarding = null;
        BoardingPoint bestDropOff = null;
        double bestWalk = Double.MAX_VALUE;
        for (BoardingPoint boarding : boardings) {
            for (BoardingPoint dropOff : dropOffs) {
                // Abordaje antes que bajada, y con un tramo que justifique subirse: dos puntos casi
                // iguales (o dos segmentos que proyectan sobre el mismo vertice) no son un viaje.
                if (dropOff.distanceAlongPathInMeters() - boarding.distanceAlongPathInMeters() < MIN_RIDE_METERS) {
                    continue;
                }
                double walk = boarding.distanceInMeters() + dropOff.distanceInMeters();
                if (walk < bestWalk) {
                    bestWalk = walk;
                    bestBoarding = boarding;
                    bestDropOff = dropOff;
                }
            }
        }
        return bestBoarding == null
                ? Optional.empty()
                : Optional.of(new RouteMatch(this, bestBoarding, bestDropOff));
    }

    /**
     * Distancia en linea recta del punto al trazado, o vacio sin trazado. Alimenta el 404 con la ruta
     * mas cercana a cada punta.
     */
    public OptionalDouble distanceToPathInMeters(Coordinate from) {
        return boardingPointFor(from).map(b -> OptionalDouble.of(b.distanceInMeters())).orElse(OptionalDouble.empty());
    }

    /** Tramo del trazado entre dos distancias acumuladas (ver {@link PathProjection#subPath}). */
    public List<Coordinate> pathBetween(double fromAlongInMeters, double toAlongInMeters) {
        return PathProjection.subPath(path, fromAlongInMeters, toAlongInMeters);
    }

    /**
     * Punto del trazado mas cercano a {@code from} y cuanto hay que caminar hasta el
     * (RF-04, ultima milla). Sirve igual para el abordaje (desde el origen) y para la
     * bajada (hacia el destino), y da el mismo resultado online y offline: es
     * geometria sobre el GeoJSON propio, sin llamar a Google.
     *
     * @return vacio si la ruta no tiene trazado cargado todavia. Una ruta asi sigue
     *     siendo buscable; simplemente no se le puede calcular la ultima milla.
     */
    public Optional<BoardingPoint> boardingPointFor(Coordinate from) {
        return PathProjection.nearestPointOn(path, from);
    }

    /**
     * Punto del trazado a la altura del barrio {@code name} (SCRUM-130): sirve cuando
     * no hay una coordenada real del cliente para esa punta — el selector de barrio
     * del frontend solo entrega un nombre, nunca una posicion, y la geolocalizacion
     * del navegador solo cubre el origen, nunca el destino.
     *
     * Es una aproximacion deliberada: supone que los barrios se reparten de forma
     * uniforme a lo largo del trazado (el barrio en el indice {@code i} de
     * {@code neighborhoods} cae a la fraccion {@code i / (size - 1)} del recorrido),
     * que es lo unico que se puede asumir sin pedirle a alguien que camine la ruta con
     * un GPS. No es un centroide real del barrio.
     *
     * @return vacio si la ruta no tiene trazado cargado todavia o si {@code name} no
     *     coincide con ningun lugar de {@link #neighborhoods()} de esta ruta
     */
    public Optional<Coordinate> pointAtNeighborhood(String name) {
        // Misma regla de coincidencia que usa la busqueda para elegir candidatas: si
        // ahi 'u hospital exito' calza con 'U. Hospital Éxito', aqui tambien, o la
        // ruta entraria como candidata y luego se quedaria sin punto de abordaje.
        OptionalInt match = indexOfPlace(name);
        if (match.isEmpty() || path.size() < 2) {
            return Optional.empty();
        }
        int index = match.getAsInt();
        double fraction = neighborhoods.size() == 1 ? 0d : (double) index / (neighborhoods.size() - 1);
        return Optional.of(PathProjection.pointAtFraction(path, fraction));
    }

    /**
     * Cuanto hay que viajar en buseta sobre esta ruta entre dos puntos, siguiendo el
     * trazado y no en linea recta.
     *
     * Es el criterio con el que se elige en modo offline: sin red no se puede pedir el
     * tiempo a Google, asi que gana la ruta de menor recorrido. Se mide entre las
     * proyecciones del origen y del destino, asi que no cuenta el trazado que la ruta
     * recorre antes de que el usuario suba ni despues de que se baje.
     *
     * @return vacio si la ruta no tiene trazado cargado
     */
    public OptionalDouble travelDistanceInMeters(Coordinate origin, Coordinate destination) {
        Optional<BoardingPoint> boarding = boardingPointFor(origin);
        Optional<BoardingPoint> dropOff = boardingPointFor(destination);
        if (boarding.isEmpty() || dropOff.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Math.abs(
                dropOff.get().distanceAlongPathInMeters() - boarding.get().distanceAlongPathInMeters()));
    }

    /**
     * Regla de descarte de RF-04: la ruta deja de ser candidata si obliga a caminar
     * mas de {@link BoardingPoint#MAX_WALKING_DISTANCE_METERS} en cualquiera de las
     * dos puntas.
     *
     * Una ruta sin trazado nunca se descarta por esta via: no hay con que medirla, y
     * sacarla del resultado la haria invisible por un dato que falta, no por una
     * razon de negocio.
     */
    public boolean isWithinWalkingDistance(Coordinate origin, Coordinate destination) {
        return isWithinWalkingDistance(origin) && isWithinWalkingDistance(destination);
    }

    /**
     * Como {@link #isWithinWalkingDistance(Coordinate, Coordinate)}, pero solo evalua
     * las puntas que el propio cliente mando (SCRUM-130). Sin coordenada del cliente
     * no hay nada que medir en esa punta: el punto que {@link #pointAtNeighborhood}
     * deriva del nombre del barrio esta siempre sobre el trazado (distancia ~0), asi
     * que evaluarlo contra este filtro seria dejar de filtrar sin decirlo.
     */
    public boolean isWithinWalkingDistance(Optional<Coordinate> origin, Optional<Coordinate> destination) {
        return origin.map(this::isWithinWalkingDistance).orElse(true)
                && destination.map(this::isWithinWalkingDistance).orElse(true);
    }

    private boolean isWithinWalkingDistance(Coordinate from) {
        return boardingPointFor(from)
                .map(BoardingPoint::isWithinWalkingDistance)
                .orElse(true);
    }

    /**
     * Devuelve la ruta con solo las tarifas vigentes a {@code today}: por cada punto
     * de referencia, la fila con el {@code validFrom} mas reciente que no sea futuro.
     * Las filas futuras se descartan (una tarifa anunciada no rige todavia).
     *
     * El orden es por precio de menor a mayor (RF-06) y no por {@code orderIndex}
     * solo: una ruta puede tener dos series de vigencia cuyos indices se solapan
     * (ver V3), y ordenar solo por indice las mezclaria.
     */
    public Route withCurrentFares(LocalDate today) {
        // HashMap y no Collectors.toMap: el punto de referencia es null en la tarifa urbana.
        Map<String, Fare> latestByPoint = new HashMap<>();
        for (Fare fare : fares) {
            if (fare.validFrom().isAfter(today)) {
                continue;
            }
            latestByPoint.merge(fare.referencePoint(), fare,
                    (a, b) -> b.validFrom().isAfter(a.validFrom()) ? b : a);
        }
        List<Fare> current = latestByPoint.values().stream()
                .sorted(Comparator.comparing(Fare::amount).thenComparingInt(Fare::orderIndex))
                .toList();
        return new Route(id, name, signFile, type, status, neighborhoods, current, path);
    }

    /**
     * Fecha de ultima actualizacion del pasaje (RF-06): la mas reciente entre los
     * {@code validFrom} de las tarifas presentes en esta instancia. Llamar despues
     * de {@link #withCurrentFares} para que refleje solo lo vigente, no anuncios
     * futuros.
     */
    public Optional<LocalDate> lastFareUpdate() {
        return fares.stream().map(Fare::validFrom).max(Comparator.naturalOrder());
    }
}
