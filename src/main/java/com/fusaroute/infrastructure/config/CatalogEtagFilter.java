package com.fusaroute.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import java.util.regex.Pattern;

/**
 * ETag del catalogo publico de rutas (RNF-01, SCRUM-127): {@code GET /api/routes} y
 * {@code GET /api/routes/{id}}, y nada mas.
 *
 * <p>Cuando el max-age vence, el navegador no vuelve a descargar el cuerpo: manda
 * {@code If-None-Match} y, si el catalogo no cambio, recibe un {@code 304} sin cuerpo.
 * El ETag es un hash del cuerpo ya serializado, asi que un cambio real en la ruta (o el
 * aviso de trancon del detalle, que depende de la hora) cambia el ETag solo.
 *
 * <p>Honestidad sobre lo que ahorra: el servidor igual consulta la base y serializa
 * antes de poder comparar el hash. Un 304 ahorra bytes en la red, no trabajo del
 * servidor; lo que quita el trabajo del servidor es el max-age, que ni siquiera llega
 * a preguntar.
 *
 * <p>La lista de rutas esta cerrada a proposito. Un filtro de ETag sobre {@code /api/**}
 * entero tocaria respuestas con datos de sesion ({@code /api/users/**},
 * {@code /api/auth/**}); {@code /api/routes/search} tampoco entra: depende de Google
 * Maps y de la hora, y ya tiene su propia cache de 5 min en el backend.
 */
@Component
public class CatalogEtagFilter extends ShallowEtagHeaderFilter {

    /** El listado, o el detalle de una ruta por id numerico. No casa con /search ni con /{id}/fare. */
    private static final Pattern CATALOG_PATH = Pattern.compile("^/api/(routes(/\\d+)?|places)/?$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod())
                || !CATALOG_PATH.matcher(request.getRequestURI()).matches();
    }
}
