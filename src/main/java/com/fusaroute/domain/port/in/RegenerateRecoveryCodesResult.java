package com.fusaroute.domain.port.in;

import java.util.List;

/**
 * Resultado de regenerar los codigos de respaldo (SCRUM-179): el conjunto nuevo
 * en claro. Se muestra UNA sola vez; el servidor solo guarda los hashes. Los
 * codigos anteriores ya no sirven.
 */
public record RegenerateRecoveryCodesResult(List<String> codes) {
}
