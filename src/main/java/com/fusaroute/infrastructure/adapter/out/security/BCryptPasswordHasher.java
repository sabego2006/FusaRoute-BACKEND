package com.fusaroute.infrastructure.adapter.out.security;

import com.fusaroute.domain.port.out.PasswordHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Adaptador de hasheo con BCrypt, costo 12.
 *
 * El costo (factor de trabajo) es 12 y no el 10 por defecto de Spring: RNF-06
 * exige >= 10, y 12 da un margen razonable frente a hardware moderno sin castigar
 * la latencia del registro. Es el unico sitio del backend que conoce el algoritmo
 * concreto; el dominio solo ve el puerto.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasherPort {

    private static final int COST = 12;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(COST);

    // Hash de relleno para cuando el correo no existe: hace que matches() gaste el
    // mismo tiempo de BCrypt que con una cuenta real (mitigacion best-effort de la
    // enumeracion de usuarios por temporizacion).
    private final String dummyHash = encoder.encode("relleno-para-igualar-tiempos");

    @Override
    public String hash(String raw) {
        return encoder.encode(raw);
    }

    @Override
    public boolean matches(String raw, String hashOrNull) {
        if (hashOrNull == null) {
            encoder.matches(raw, dummyHash);
            return false;
        }
        return encoder.matches(raw, hashOrNull);
    }
}
