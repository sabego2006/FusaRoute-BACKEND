package com.fusaroute.infrastructure.adapter.out.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Borra lo anterior a {@code app.logs.retention-days} (30) en {@code app_logs} y
 * {@code search_events} (SCRUM-186).
 *
 * <p>Corre dos veces: a diario por {@code @Scheduled} y una vez al arrancar. La segunda
 * es la importante: el backend vive en el plan gratis de Render, que lo duerme tras 15
 * minutos sin trafico, asi que un cron diario puede no llegar a dispararse nunca. Cada
 * arranque en frio hace la limpieza que el cron no pudo.
 *
 * <p>Una tabla no cuenta como "purgada" si la otra fallo: cada una va en su propio
 * intento, y un error se registra como WARN (que a su vez queda en {@code app_logs}).
 */
@Component
class MonitoringDataPurger {

    private static final Logger log = LoggerFactory.getLogger(MonitoringDataPurger.class);

    private final JdbcAppLogStore logs;
    private final JdbcUsageEventStore events;
    private final Clock clock;
    private final int retentionDays;

    MonitoringDataPurger(JdbcAppLogStore logs, JdbcUsageEventStore events, Clock clock,
                         @Value("${app.logs.retention-days:30}") int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("app.logs.retention-days debe ser al menos 1");
        }
        this.logs = logs;
        this.events = events;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    /** 03:15 hora de Colombia: la hora de menos uso, y fuera de la franja habil del RNF-02. */
    @Scheduled(cron = "${app.logs.purge-cron:0 15 3 * * *}", zone = "America/Bogota")
    void purgeScheduled() {
        purgeNow();
    }

    /** Devuelve cuantas filas borro en total; las tablas que fallen cuentan cero. */
    int purgeNow() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(retentionDays));
        int deleted = 0;
        try {
            deleted += logs.deleteOlderThan(cutoff);
        } catch (RuntimeException e) {
            log.warn("No se pudo purgar app_logs (corte {}): {}", cutoff, e.getMessage());
        }
        try {
            deleted += events.deleteOlderThan(cutoff);
        } catch (RuntimeException e) {
            log.warn("No se pudo purgar search_events (corte {}): {}", cutoff, e.getMessage());
        }
        log.info("Purga de monitoreo: {} filas anteriores a {} ({} dias)", deleted, cutoff, retentionDays);
        return deleted;
    }
}
