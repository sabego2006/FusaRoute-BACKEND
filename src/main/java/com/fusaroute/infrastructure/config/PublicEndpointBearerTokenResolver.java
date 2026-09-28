package com.fusaroute.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Resuelve el Bearer token solo en rutas protegidas. En rutas publicas
 * ({@code /health}, {@code /api/auth/**}, {@code GET /api/routes/**}) ignora
 * el header Authorization, de modo que un token expirado o invalido no cause
 * un 401 en endpoints que el usuario puede consultar sin sesion.
 *
 * Sin esto, el resource server valida el token en TODA peticion que lo traiga,
 * y el interceptor del frontend (que adjunta el token guardado en localStorage)
 * rompe el catalogo publico cuando el token vencio.
 */
public class PublicEndpointBearerTokenResolver implements BearerTokenResolver {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static final List<String> PUBLIC_PATTERNS = List.of(
            "/health",
            "/api/auth/**",
            "/api/routes",
            "/api/routes/**"
    );

    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        // En rutas publicas con GET, no extraer el token.
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("GET".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
            for (String pattern : PUBLIC_PATTERNS) {
                if (MATCHER.match(pattern, path)) {
                    // Solo ignorar el token si el metodo tambien es publico.
                    if (isPublicMethodForPattern(method, pattern)) {
                        return null;
                    }
                }
            }
        }

        return delegate.resolve(request);
    }

    private boolean isPublicMethodForPattern(String method, String pattern) {
        // /health acepta cualquier metodo (pero en la practica es GET)
        if ("/health".equals(pattern)) {
            return true;
        }
        // /api/auth/** acepta POST (register y login)
        if (pattern.startsWith("/api/auth")) {
            return "POST".equalsIgnoreCase(method);
        }
        // /api/routes y /api/routes/** solo GET
        return "GET".equalsIgnoreCase(method);
    }
}
