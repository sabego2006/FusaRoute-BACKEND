package com.fusaroute.domain.port.out;

/** Metrica 1 del panel de RF-13 (SCRUM-26): cuantas cuentas hay. Solo lectura. */
@FunctionalInterface
public interface UserCountPort {

    /** Todas las cuentas registradas, de usuario o de administrador. */
    long countAll();
}
