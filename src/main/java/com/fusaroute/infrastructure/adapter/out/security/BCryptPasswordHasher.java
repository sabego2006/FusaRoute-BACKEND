package com.fusaroute.infrastructure.adapter.out.security;

import com.fusaroute.domain.port.out.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Adaptador de seguridad que implementa el hasheo de contraseñas usando BCrypt.
 * Cumple con RNF-06: factor de costo >= 10.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;

    public BCryptPasswordHasher() {
        // El valor por defecto de BCryptPasswordEncoder es 10, cumpliendo con RNF-06.
        this.encoder = new BCryptPasswordEncoder(10);
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean verify(String rawPassword, String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
