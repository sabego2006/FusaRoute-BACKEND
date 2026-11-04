package com.fusaroute.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autenticacion OPCIONAL para los endpoints que son publicos pero se comportan
 * distinto cuando hay sesion (RF-07, SCRUM-20).
 *
 * El caso concreto: {@code GET /api/routes/search} es publico —se busca ruta sin
 * cuenta— pero, si el usuario tiene sesion, la consulta debe quedar anotada en su
 * historial. Para eso el controlador necesita recibir el {@code Jwt}.
 *
 * Por que hace falta un filtro aparte y no basta con dejar que
 * {@link PublicEndpointBearerTokenResolver} devuelva el token en esas rutas:
 *
 * <ul>
 *   <li>Si el resolver devuelve el token, el {@code BearerTokenAuthenticationFilter}
 *       estandar lo valida y, cuando esta vencido o es basura, corta con 401 — en un
 *       endpoint que tiene que seguir funcionando sin sesion. Es exactamente el
 *       problema que el resolver existe para evitar.</li>
 *   <li>Si el resolver devuelve null, el controlador nunca ve el token y el historial
 *       no se escribe jamas.</li>
 * </ul>
 *
 * La salida es intentar la autenticacion aqui y tragarse el fallo: token valido,
 * sesion; token vencido o ausente, anonimo. Nunca 401. Las rutas privadas siguen
 * pasando por el filtro estandar, que si debe rechazar.
 *
 * Se registra con {@code addFilterBefore} para correr antes del
 * filtro estandar; como en estas rutas el resolver devuelve null, ese filtro no hara
 * nada despues y no pisara lo que se deje puesto aqui.
 */
public class OptionalJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OptionalJwtAuthenticationFilter.class);

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final String BEARER = "Bearer ";

    /**
     * Rutas publicas en las que, ademas, interesa saber quien es el usuario si lo hay.
     * Se enumeran una a una en vez de usar un comodin: que un endpoint publico mire el
     * token es una excepcion, y cada excepcion debe escribirse a mano para que agregar
     * otra sea una decision y no un descuido.
     */
    private static final List<String> OPTIONAL_AUTH_PATTERNS = List.of(
            "/api/routes/search",
            "/api/routes/search/offline"
    );

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter authenticationConverter;

    public OptionalJwtAuthenticationFilter(JwtDecoder jwtDecoder,
                                           JwtAuthenticationConverter authenticationConverter) {
        this.jwtDecoder = jwtDecoder;
        this.authenticationConverter = authenticationConverter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return OPTIONAL_AUTH_PATTERNS.stream()
                .noneMatch(pattern -> MATCHER.match(pattern, request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            tokenOf(request).ifPresent(this::authenticateQuietly);
        }
        chain.doFilter(request, response);
    }

    private java.util.Optional<String> tokenOf(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            return java.util.Optional.empty();
        }
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(token);
    }

    /**
     * Un token invalido deja la peticion anonima en vez de rechazarla: el endpoint es
     * publico y negarle la busqueda a alguien por tener un token vencido en
     * localStorage seria peor que no anotarle la busqueda en el historial.
     *
     * Se registra en debug y no en warn porque un token vencido es lo normal despues
     * de una semana (RNF-04), no un incidente.
     */
    private void authenticateQuietly(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            Authentication authentication = authenticationConverter.convert(jwt);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException e) {
            log.debug("Token invalido en endpoint de autenticacion opcional; se sigue como anonimo: {}",
                    e.getMessage());
        }
    }
}
