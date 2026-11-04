package com.fusaroute.domain.port.in;

/**
 * Puerto de entrada para regenerar los codigos de respaldo del segundo factor
 * (SCRUM-179).
 *
 * Existe porque los 10 codigos que se emiten al confirmar el enrolamiento son de
 * un solo uso: gastados, un usuario que pierda el telefono quedaria bloqueado
 * sin salida. Regenerar reemplaza el conjunto completo.
 *
 * Exige el segundo factor activo y token completo: no es un camino de
 * recuperacion para quien ya no puede entrar, es mantenimiento desde la sesion.
 */
public interface RegenerateRecoveryCodesUseCase {

    RegenerateRecoveryCodesResult regenerate(Long userId);
}
