package com.fusaroute.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
 * El JWT propiamente dicho (filtro, emision y validacion) llega con RF-02 en la
 * epica de Cuenta de usuario. Este archivo es solo el piso para que el ambiente
 * DEV arranque, se pueda comprobar y el frontend pueda hablarle.
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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health").permitAll()
                        .anyRequest().authenticated())
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
