package com.fusaroute.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
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
 * ROL (SCRUM-104). Todo {@code /api/admin/**} exige rol ADMIN, leido del claim
 * {@code role} del JWT. Un solo patron cubre el prefijo entero para que agregar un
 * endpoint de administrador no dependa de que alguien se acuerde de protegerlo.
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
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        return http
                // Autenticacion OPCIONAL en la busqueda publica (RF-07, SCRUM-20): corre
                // antes del filtro estandar para que, cuando llega un token valido, el
                // controlador reciba el Jwt y pueda anotar la busqueda en el historial.
                // Sin esto PublicEndpointBearerTokenResolver devuelve null en esas rutas
                // y el historial no se escribe nunca. Ver OptionalJwtAuthenticationFilter.
                .addFilterBefore(
                        new OptionalJwtAuthenticationFilter(jwtDecoder, jwtAuthenticationConverter()),
                        BearerTokenAuthenticationFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        // "/api/routes/*" cubre un solo segmento, asi que /search/offline
                        // (SCRUM-51) hay que enumerarlo aparte o quedaria exigiendo token.
                        .requestMatchers(HttpMethod.GET, "/api/routes", "/api/routes/*", "/api/routes/*/fare", "/api/places",
                                "/api/routes/search/offline",
                                "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
                                .permitAll()
                        // SCRUM-179: enrolamiento acepta token full (re-enrolar) o enrollment (bootstrap).
                        .requestMatchers(HttpMethod.POST,
                                "/api/users/me/totp/enroll",
                                "/api/users/me/totp/confirm")
                                .hasAnyAuthority(SCOPE_FULL, SCOPE_TOTP_ENROLL)
                        // ADMINISTRADOR (SCRUM-104). Un solo patron cierra todo el
                        // prefijo, en vez de enumerar endpoint por endpoint: el que
                        // alguien olvide enumerar manana queda protegido igual.
                        //
                        // Exige las DOS cosas a la vez, no cualquiera de las dos. Con
                        // hasAnyAuthority bastaria el rol, y entonces un token de
                        // enrolamiento (scope=totp_enroll) de una cuenta ADMIN abriria
                        // el panel completo, cuando su alcance deberia terminar en los
                        // dos endpoints de TOTP.
                        .requestMatchers("/api/admin/**").access(AuthorizationManagers.allOf(
                                AuthorityAuthorizationManager.hasAuthority(SCOPE_FULL),
                                AuthorityAuthorizationManager.hasRole("ADMIN")))
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
     * Mapea los claims del JWT a authorities:
     * <ul>
     *   <li>{@code scope} → "SCOPE_full" o "SCOPE_totp_enroll". Un token sin el
     *       claim se trata como "full" (compatibilidad hacia atras con tokens
     *       emitidos antes de SCRUM-179 y con los helpers de test
     *       {@code .with(jwt())}, que no setean scope).</li>
     *   <li>{@code role} → "ROLE_USER" o "ROLE_ADMIN" (SCRUM-104).</li>
     * </ul>
     */
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::readAuthorities);
        return converter;
    }

    /**
     * El claim {@code role} ya lo firmaba {@code JwtTokenIssuer} desde el primer
     * login, pero este converter lo DESCARTABA: solo leia {@code scope}. Mientras
     * no hubo endpoints por rol no se noto; con {@code /api/admin/**} (SCRUM-104)
     * hay que traducirlo, o {@code hasRole("ADMIN")} nunca se cumpliria para nadie.
     *
     * <p>Que el claim ya existiera tiene una consecuencia buena: los tokens que hoy
     * estan vigentes (duran una semana, RNF-04) ya lo traen, asi que nadie tiene que
     * reloguear por este cambio. Lo que si exige relogueo es SUBIR a alguien a
     * ADMIN, porque el rol viaja dentro del token y el que ya esta emitido sigue
     * diciendo USER hasta que venza.
     *
     * <p>Un token sin {@code role} no recibe authority de rol en vez de recibir una
     * por defecto: ante la duda, sin privilegios. Lo unico que queda cerrado para el
     * es {@code /api/admin/**}, que es exactamente lo que se quiere.
     */
    private static Collection<GrantedAuthority> readAuthorities(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        String value = (scope == null || scope.isBlank()) ? "full" : scope.trim();

        List<GrantedAuthority> authorities = new ArrayList<>(2);
        authorities.add(new SimpleGrantedAuthority("SCOPE_" + value));

        String role = jwt.getClaimAsString("role");
        if (role != null && !role.isBlank()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.trim()));
        }
        return authorities;
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
