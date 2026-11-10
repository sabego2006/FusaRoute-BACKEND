package com.fusaroute.domain.exception;

/** Parametros invalidos al consultar logs o eventos de uso (SCRUM-186): se responde 400. */
public class InvalidMonitoringQueryException extends RuntimeException {

    public InvalidMonitoringQueryException(String message) {
        super(message);
    }
}
