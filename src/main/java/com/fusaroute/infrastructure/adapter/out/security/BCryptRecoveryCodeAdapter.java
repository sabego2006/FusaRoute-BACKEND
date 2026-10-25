package com.fusaroute.infrastructure.adapter.out.security;

import com.fusaroute.domain.port.out.RecoveryCodePort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador de codigos de respaldo del segundo factor (SCRUM-179).
 *
 * Los codigos en claro los genera un {@link SecureRandom}: 10 caracteres de un
 * alfabeto sin 0/O ni 1/l para que el usuario pueda copiarlos a mano sin
 * confundirlos. Se guardan en BD como hash BCrypt (RNF-06, mismo encoder que
 * las contrasenas) para que, si roban la base, no sirvan a nadie. El usuario
 * los ve UNA sola vez y nunca mas los conoce el servidor.
 */
@Component
public class BCryptRecoveryCodeAdapter implements RecoveryCodePort {

    /** Mismo costo que BCryptPasswordHasher: >= 10 por RNF-06. */
    private static final int COST = 12;

    /** Alfabeto sin 0/O ni 1/l para evitar confusiones al copiar a mano. */
    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final int CODE_LENGTH = 10;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(COST);
    private final SecureRandom random = new SecureRandom();

    @Override
    public List<String> generatePlainCodes(int count) {
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            codes.add(randomCode());
        }
        return codes;
    }

    @Override
    public String hash(String plain) {
        return encoder.encode(plain);
    }

    @Override
    public boolean matches(String plain, String hashOrNull) {
        if (hashOrNull == null || plain == null) {
            return false;
        }
        return encoder.matches(plain, hashOrNull);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
