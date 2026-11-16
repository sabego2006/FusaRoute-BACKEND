package com.fusaroute.infrastructure.adapter.out.security;

import com.fusaroute.domain.port.out.PasswordHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

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
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(COST);

    // Filler hash used when the email does not exist: makes matches() spend the same
    // BCrypt time as with a real account (best-effort mitigation of user enumeration
    // by timing). The filler is generated at runtime from random bytes, never from a
    // literal: only the cost of the comparison matters, the value is irrelevant and
    // a hard-coded one would read as a credential (Sonar S6437).
    private final String dummyHash = encoder.encode(randomFiller());

    private static String randomFiller() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

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
