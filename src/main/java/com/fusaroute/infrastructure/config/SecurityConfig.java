package com.fusaroute.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuracion minima de seguridad y de CORS.
 *
 * SEGURIDAD. Existe porque al agregar spring-boot-starter-security, Spring cierra
 * TODOS los endpoints por defecto detras de autenticacion basica, y /health
 * dejaria de responder. La regla es la conservadora: se abre /health, que es
 * publico a proposito porque lo consulta el healthcheck externo de la metrica de
 * fiabilidad, y todo lo demas exige autenticacion.
 *
 * CSRF se desactiva porque la API es sin estado y se autentica con JWT en el
 * header Authorization, no con cookie de sesion: sin cookie no hay vector CSRF.
 *
 * CORS. El frontend corre en un origen distinto del backend (4200 contra 8080 en
 * DEV), asi que sin esto el navegador bloquea toda llamada del Angular a la API.
 * Los origenes permitidos salen de una variable de entorno, no de una lista
 * escrita a mano: en DEV es localhost:4200 y en PRE/PROD sera el dominio real.
 *
 * Nota deliberada: se enumeran los origenes uno por uno y NO se usa "*". Con
 * credenciales habilitadas el comodin ni siquiera es valido, y aunque lo fuera,
 * abrir la API a cualquier origen es regalar la superficie de ataque.
 *
 * El registro (POST /api/auth/register, RF-01) y el login (POST /api/auth/login,
 * RF-02) son publicos: quien los llama todavia no tiene con que autenticarse.
 *
 * JWT (SCRUM-13). La API es un resource server: valida el header
 * "Authorization: Bearer <jwt>" con el JwtDecoder de {@code JwtConfig} y no crea
 * sesion (STATELESS). Sin token, o con uno invalido o vencido, la respuesta es 401.
 * Aun no hay conversion de rol a autoridad: llega con el primer endpoint de admin.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
                        .requestMatchers("/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
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
