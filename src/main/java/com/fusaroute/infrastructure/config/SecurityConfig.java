package com.fusaroute.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.List;

/**
 * Configuracion de seguridad y CORS.
 *
 * SEGURIDAD. Al agregar spring-boot-starter-security, Spring cierra TODOS los
 * endpoints por defecto. La regla es conservadora: se abren los publicos
 * (/health, registro, login, catalogo de rutas) y todo lo demas exige JWT.
 *
 * CSRF desactivado: la API es sin estado y se autentica con JWT en el header
 * Authorization, no con cookie de sesion.
 *
 * CORS. El frontend corre en un origen distinto (4200 contra 8080 en DEV).
 * Los origenes salen de una variable de entorno; nunca se usa "*".
 *
 * BEARER TOKEN RESOLVER personalizado: ignora el token en rutas publicas, para
 * que un token expirado en localStorage no rompa el catalogo ni /health.
 *
 * ENTRY POINT y ACCESS DENIED HANDLER en JSON: evitan respuestas 401/403 con
 * cuerpo vacio.
 *
 * ACTUATOR (RNF-01, SCRUM-127): /actuator/health es publico porque un balanceador
 * o UptimeRobot lo consulta sin credenciales (RNF-02); el resto de /actuator/**
 * (metrics, prometheus) exige JWT porque expone datos operativos internos.
 * El probe interno de /api/_internal/** exige JWT por la misma razon.
 *
 * ALCANCE POR SCOPE (SCRUM-179). El JWT lleva un claim {@code scope} con dos
 * valores posibles: "full" (token de 7 dias, abre toda la API) o
 * "totp_enroll" (token corto que SOLO sirve para enrolar y confirmar TOTP).
 * {@link #jwtAuthenticationConverter()} lo traduce a authority
 * {@code SCOPE_full}/{@code SCOPE_totp_enroll}; un token sin el claim se trata
 * como "full" para que los tokens emitidos antes de SCRUM-179 (aun vigentes)
 * sigan funcionando. Las rutas protegidas exigen {@code SCOPE_full} salvo los
 * dos endpoints de enrolamiento, que admiten cualquiera de los dos.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String SCOPE_FULL = "SCOPE_full";
    static final String SCOPE_TOTP_ENROLL = "SCOPE_totp_enroll";

    private final List<String> allowedOrigins;

    public SecurityConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = List.of(allowedOrigins.split("\s*,\s*"));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/routes", "/api/routes/*", "/api/routes/*/fare",
                                "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
                                .permitAll()
                        // SCRUM-179: enrolamiento acepta token full (re-enrolar) o enrollment (bootstrap).
                        .requestMatchers(HttpMethod.POST,
                                "/api/users/me/totp/enroll",
                                "/api/users/me/totp/confirm")
                                .hasAnyAuthority(SCOPE_FULL, SCOPE_TOTP_ENROLL)
                        // Todo lo demas exige el token full. Un token de enrolamiento
                        // robado no sirve para leer el perfil ni para nada mas.
                        .anyRequest().hasAuthority(SCOPE_FULL))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(new PublicEndpointBearerTokenResolver())
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                        .accessDeniedHandler(new JsonAccessDeniedHandler())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }

    /**
     * Mapea el claim {@code scope} del JWT a authorities ("SCOPE_full" o
     * "SCOPE_totp_enroll"). Un token sin el claim se trata como "full"
     * (compatibilidad hacia atras con tokens emitidos antes de SCRUM-179 y con
     * los helpers de test {@code .with(jwt())}, que no setean scope).
     */
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::readScopeAuthorities);
        return converter;
    }

    private static Collection<GrantedAuthority> readScopeAuthorities(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        String value = (scope == null || scope.isBlank()) ? "full" : scope.trim();
        return List.of(new SimpleGrantedAuthority("SCOPE_" + value));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
