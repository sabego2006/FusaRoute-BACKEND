package com.fusaroute.domain.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Las cinco metricas del panel del administrador (RF-13, SCRUM-26) y los metadatos para leerlas.
 * Se calcula al pedirse; no se guarda ni se cachea.
 *
 * @param totalUsers        cuentas registradas, de cualquier rol.
 * @param searchesToday     busquedas del dia calendario actual en {@code timezone}.
 * @param topRoutes         hasta 5 rutas, por busquedas desc y luego por nombre.
 * @param pendingComments   comentarios en estado pendiente de respuesta.
 * @param activeSuspensions suspensiones efectivamente vigentes al generar el panel.
 * @param generatedAt       instante del calculo, con el offset de {@code timezone}.
 * @param timezone          zona con la que se decide "hoy".
 * @param topRoutesWindowDays dias hacia atras que mira el ranking de rutas.
 */
public record AdminMetrics(
        long totalUsers,
        long searchesToday,
        List<TopRoute> topRoutes,
        long pendingComments,
        long activeSuspensions,
        OffsetDateTime generatedAt,
        String timezone,
        int topRoutesWindowDays) {

    public AdminMetrics {
        topRoutes = List.copyOf(topRoutes);
    }
}
