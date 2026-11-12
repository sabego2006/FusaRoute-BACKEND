package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.FieldViolation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Datos de una ruta que el administrador crea o modifica (RF-11). Contiene las reglas
 * de validacion: el controlador solo traduce el JSON a este objeto.
 *
 * <p>El trazado llega como {@code pathType} + {@code pathPositions} (cada posicion es
 * {@code [longitud, latitud]}, el orden de GeoJSON) y no como un objeto JSON, para que el
 * dominio no dependa de ninguna libreria. Sin trazado, ambos van nulos.
 */
public record RouteDraft(String name, RouteType type, String pathType, List<List<Double>> pathPositions,
                         List<String> neighborhoods, List<FareDraft> fares, String signFile) {

    public static final int NAME_MIN = 3;
    public static final int NAME_MAX = 140;
    public static final int NEIGHBORHOODS_MAX = 40;
    public static final int NEIGHBORHOOD_MAX_LENGTH = 120;
    public static final int REFERENCE_POINT_MAX_LENGTH = 160;
    private static final BigDecimal AMOUNT_LIMIT = new BigDecimal("100000000");
    /** Mismo formato que ck_routes_sign_file (V14). */
    private static final Pattern SIGN_FILE = Pattern.compile("^[A-Z0-9_]+$");

    public boolean hasPath() {
        return pathType != null || pathPositions != null;
    }

    /** Todos los incumplimientos juntos, para que el formulario los pinte de una vez. */
    public List<FieldViolation> violations() {
        List<FieldViolation> out = new ArrayList<>();
        validateName(out);
        if (type == null) {
            out.add(new FieldViolation("type", "El tipo de ruta es obligatorio (URBANA o INTERMUNICIPAL)"));
        }
        validatePath(out);
        validateNeighborhoods(out);
        validateFares(out);
        validateSignFile(out);
        return out;
    }

    private void validateName(List<FieldViolation> out) {
        if (name == null || name.isBlank()) {
            out.add(new FieldViolation("name", "El nombre es obligatorio"));
            return;
        }
        if (!name.equals(name.strip().replaceAll("\\s+", " "))) {
            out.add(new FieldViolation("name", "El nombre no puede tener espacios sobrantes"));
        }
        if (name.strip().length() < NAME_MIN || name.length() > NAME_MAX) {
            out.add(new FieldViolation("name",
                    "El nombre debe tener entre " + NAME_MIN + " y " + NAME_MAX + " caracteres"));
        }
    }

    private void validatePath(List<FieldViolation> out) {
        if (!hasPath()) {
            return;
        }
        if (!"LineString".equals(pathType)) {
            out.add(new FieldViolation("pathGeoJson", "El trazado debe ser un GeoJSON de tipo LineString"));
            return;
        }
        if (pathPositions == null || pathPositions.size() < 2) {
            out.add(new FieldViolation("pathGeoJson", "El trazado necesita al menos 2 posiciones"));
            return;
        }
        for (List<Double> position : pathPositions) {
            if (position == null || position.size() < 2 || position.get(0) == null || position.get(1) == null) {
                out.add(new FieldViolation("pathGeoJson", "Cada posicion del trazado debe ser [longitud, latitud]"));
                return;
            }
            double lon = position.get(0);
            double lat = position.get(1);
            if (Double.isNaN(lon) || Double.isNaN(lat) || lon < -180 || lon > 180 || lat < -90 || lat > 90) {
                out.add(new FieldViolation("pathGeoJson",
                        "Posicion fuera de rango: la longitud va de -180 a 180 y la latitud de -90 a 90"));
                return;
            }
        }
    }

    private void validateNeighborhoods(List<FieldViolation> out) {
        if (neighborhoods == null || neighborhoods.isEmpty() || neighborhoods.size() > NEIGHBORHOODS_MAX) {
            out.add(new FieldViolation("neighborhoods",
                    "La ruta debe pasar por entre 1 y " + NEIGHBORHOODS_MAX + " barrios"));
            return;
        }
        Set<String> seen = new HashSet<>();
        for (String neighborhood : neighborhoods) {
            if (neighborhood == null || neighborhood.isBlank()) {
                out.add(new FieldViolation("neighborhoods", "Los nombres de barrio no pueden ir vacios"));
                return;
            }
            if (neighborhood.strip().length() > NEIGHBORHOOD_MAX_LENGTH) {
                out.add(new FieldViolation("neighborhoods",
                        "Cada barrio puede tener hasta " + NEIGHBORHOOD_MAX_LENGTH + " caracteres"));
                return;
            }
            if (!seen.add(neighborhood.strip().toLowerCase(Locale.ROOT))) {
                out.add(new FieldViolation("neighborhoods", "Hay barrios repetidos: " + neighborhood.strip()));
                return;
            }
        }
    }

    private void validateFares(List<FieldViolation> out) {
        if (fares == null || fares.isEmpty()) {
            out.add(new FieldViolation("fares", "La ruta necesita al menos una tarifa"));
            return;
        }
        for (FareDraft fare : fares) {
            if (fare == null || fare.amount() == null || fare.amount().signum() <= 0) {
                out.add(new FieldViolation("fares", "Cada tarifa debe tener un monto mayor que cero"));
                return;
            }
            if (fare.amount().compareTo(AMOUNT_LIMIT) >= 0 || fare.amount().stripTrailingZeros().scale() > 2) {
                out.add(new FieldViolation("fares", "El monto debe ser menor a 100000000 y tener maximo 2 decimales"));
                return;
            }
        }
        if (type == RouteType.URBANA) {
            if (fares.size() != 1 || fares.get(0).referencePoint() != null) {
                out.add(new FieldViolation("fares",
                        "Una ruta urbana lleva exactamente una tarifa, sin punto de referencia"));
            }
        } else if (type == RouteType.INTERMUNICIPAL) {
            Set<String> points = new HashSet<>();
            for (FareDraft fare : fares) {
                String point = fare.referencePoint();
                if (point == null || point.isBlank()) {
                    out.add(new FieldViolation("fares",
                            "Cada tarifa de una ruta intermunicipal necesita su punto de referencia"));
                    return;
                }
                if (point.strip().length() > REFERENCE_POINT_MAX_LENGTH) {
                    out.add(new FieldViolation("fares", "El punto de referencia admite hasta "
                            + REFERENCE_POINT_MAX_LENGTH + " caracteres"));
                    return;
                }
                if (!points.add(point.strip().toLowerCase(Locale.ROOT))) {
                    out.add(new FieldViolation("fares", "Hay puntos de referencia repetidos: " + point.strip()));
                    return;
                }
            }
        }
    }

    private void validateSignFile(List<FieldViolation> out) {
        if (signFile != null && (signFile.length() > NAME_MAX || !SIGN_FILE.matcher(signFile).matches())) {
            out.add(new FieldViolation("signFile",
                    "El letrero debe ser MAYUSCULAS, digitos y guion bajo (hasta " + NAME_MAX + " caracteres)"));
        }
    }
}
