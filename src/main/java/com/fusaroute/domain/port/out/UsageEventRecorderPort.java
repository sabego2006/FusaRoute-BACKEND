package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.UsageEvent;

/**
 * Registra un evento de uso (SCRUM-186). Lo usan la busqueda de rutas y "Ya me
 * subi" (conectados en P6).
 *
 * <p><b>Contrato:</b> {@link #record} nunca bloquea al llamador ni lanza excepcion. Si
 * la base esta lenta o caida, el evento se descarta y se cuenta; la peticion del usuario
 * no puede esperar ni fallar por haber querido anotar una estadistica.
 */
public interface UsageEventRecorderPort {

    /** Encola el evento y vuelve de inmediato. Si no cabe, lo descarta sin avisar al llamador. */
    void record(UsageEvent event);
}
