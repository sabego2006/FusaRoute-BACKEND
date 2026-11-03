package com.fusaroute.domain.model;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalizacion del nombre de un lugar del recorrido (SCRUM-184).
 *
 * Existe por un problema real de los datos, no por elegancia. Los nombres de
 * {@code route_neighborhoods} los carga a mano el trabajo de campo, de fuentes
 * distintas y en rondas distintas, y la deriva es inevitable: V12 sembro
 * {@code 'Terminal de Transporte'} en una ruta y {@code 'Terminal de transportes'} en
 * otra, {@code 'Centro Comercial Avenida'} frente a {@code 'Centro comercial avenida'}.
 * Mientras la busqueda comparaba con {@link String#equals}, esos pares nunca se
 * cruzaban: dos rutas que pasan por el mismo sitio quedaban incomunicadas por una
 * mayuscula.
 *
 * La normalizacion es deliberadamente agresiva y solo para comparar: el nombre que se
 * le muestra al usuario siempre es el original, tal como lo registro el campo.
 *
 * Vive en el dominio porque es una regla de negocio ("cuando dos nombres de lugar son
 * el mismo lugar"), no un detalle de infraestructura. {@code java.text} es JDK puro,
 * asi que no rompe la regla de ArchUnit que prohibe frameworks en {@code domain/}.
 */
final class PlaceName {

    private PlaceName() {
    }

    /**
     * Forma comparable de un nombre de lugar: minusculas, sin tildes ni diacriticos,
     * sin puntos y con los espacios colapsados.
     *
     * <p>Se quitan los puntos porque las abreviaturas del campo van escritas de las dos
     * formas ({@code 'Cra. 6'} y {@code 'Cra 6'}, {@code 'U. Hospital Exito'} y
     * {@code 'U Hospital Exito'}) y son el mismo lugar.
     *
     * @param value nombre original; {@code null} devuelve cadena vacia, que no coincide
     *     con ningun lugar real y evita un {@code NullPointerException} en el camino de
     *     la busqueda
     */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        // NFD separa la letra de su diacritico, y entonces \p{M} los borra uno por uno.
        // Es lo que hace que 'Maíz' y 'Maiz' sean el mismo texto al comparar.
        String sinTildes = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sinTildes.toLowerCase(Locale.ROOT)
                .replace(".", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
