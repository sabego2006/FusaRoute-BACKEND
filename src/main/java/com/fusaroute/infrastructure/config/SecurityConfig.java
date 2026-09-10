package com.fusaroute.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracion minima de seguridad.
 *
 * Existe porque al agregar spring-boot-starter-security, Spring cierra TODOS los
 * endpoints por defecto detras de autenticacion basica, y /health dejaria de
 * responder. La regla de este archivo es la conservadora: se abre /health, que
 * es publico a proposito porque lo consulta el healthcheck externo de la metrica
 * de fiabilidad, y todo lo demas exige autenticacion.
 *
 * CSRF se desactiva porque la API es sin estado y se autentica con JWT en el
 * header Authorization, no con cookie de sesion: sin cookie no hay vector CSRF.
 *
 * El JWT propiamente dicho (filtro, emision y validacion) llega con RF-02 en la
 * epica de Cuenta de usuario. Este archivo es solo el piso para que el ambiente
 * DEV arranque y se pueda comprobar.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }
}
