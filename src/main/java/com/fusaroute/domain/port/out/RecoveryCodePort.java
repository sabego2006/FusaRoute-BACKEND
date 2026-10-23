package com.fusaroute.domain.port.out;

import java.util.List;

/**
 * Puerto de salida para los codigos de respaldo del segundo factor (SCRUM-179).
 *
 * Genera los codigos en claro — los recibe el usuario una sola vez — y los
 * hashea para guardarlos. {@link #matches(String, String)} verifica si un
 * codigo que el usuario envia encaja contra el hash guardado, usando el mismo
 * BCrypt que las contrasenas (RNF-06, costo >= 10).
 */
public interface RecoveryCodePort {

    /**
     * Genera {@code count} codigos aleatorios en claro, legibles por humanos y
     * sin ambigüedades tipograficas (sin 0/O ni 1/l).
     */
    List<String> generatePlainCodes(int count);

    /** Hashea un codigo en claro para persistirlo. */
    String hash(String plain);

    /**
     * Verifica un codigo en claro contra un hash guardado. {@code hashOrNull}
     * admite null para que un caso de uso que itera sobre varios candidatos no
     * tenga que ramificar entre existentes y faltantes.
     */
    boolean matches(String plain, String hashOrNull);
}
