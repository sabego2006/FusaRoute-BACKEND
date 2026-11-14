package com.fusaroute.domain.port.out;

import java.time.Instant;

/**
 * Metrica 5 del panel de RF-13 (SCRUM-26): cuantas suspensiones estan vigentes. El adaptador usa el mismo
 * predicado que {@link ActiveSuspensionsPort} ({@code starts_at <= ahora < ends_at}, sin levantar), asi el
 * panel y el aviso al usuario no pueden discrepar sobre que es "vigente".
 */
@FunctionalInterface
public interface EffectiveSuspensionCountPort {

    long countEffectiveAt(Instant now);
}
