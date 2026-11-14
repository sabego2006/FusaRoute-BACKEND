package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.AdminMetrics;
import com.fusaroute.domain.model.RouteSearchCount;
import com.fusaroute.domain.model.TopRoute;
import com.fusaroute.domain.port.in.CountPendingFeedbackUseCase;
import com.fusaroute.domain.port.in.GetAdminMetricsUseCase;
import com.fusaroute.domain.port.out.EffectiveSuspensionCountPort;
import com.fusaroute.domain.port.out.RouteNamePort;
import com.fusaroute.domain.port.out.UsageStatsPort;
import com.fusaroute.domain.port.out.UserCountPort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Panel de metricas de uso del administrador (RF-13, SCRUM-26). Cada llamada recalcula: sin cache, sin
 * actualizacion en vivo.
 *
 * <p><b>El dia es el de Colombia.</b> "Hoy" va de las 00:00 (inclusive) a las 00:00 del dia siguiente
 * (exclusive) en {@code America/Bogota}, calculado con el {@link Clock} inyectado: el backend corre en Render
 * (UTC) y, sin esto, a partir de las 19:00 en Colombia el dia ya seria el de manana. Un evento a las
 * 23:59 -05:00 cuenta para ese dia; uno a las 00:00 -05:00 del siguiente, no.
 *
 * <p><b>Desviacion respecto de RF-13 (metrica 3).</b> La historia pide las 5 rutas mas consultadas del
 * historial acumulado, pero {@code search_history} no guarda la ruta y {@code search_events} se purga a 30
 * dias. El ranking sale entonces de {@code search_events}: eventos {@code SEARCH} con ruta de los ultimos
 * {@value GetAdminMetricsUseCase#TOP_ROUTES_WINDOW_DAYS} dias. Ver {@code docs/contratos/rf13-metricas.md}.
 *
 * <p>El ranking se arma asi: primero se descartan las rutas que ya no existen, despues se ordena por busquedas
 * descendente y a igual numero por nombre, y al final se recorta a 5. Descartar antes de recortar evita que
 * una ruta borrada deje el ranking con menos de 5 teniendo rutas de sobra. Una ruta INACTIVA o suspendida se
 * muestra igual: es historia, no estado actual.
 *
 * <p>"Pendientes" sale de {@link CountPendingFeedbackUseCase} y no del repositorio, para que sea por
 * construccion el mismo estado que bloquea la edicion de un comentario (RF-08/RF-14).
 */
public class GetAdminMetricsService implements GetAdminMetricsUseCase {

    private static final ZoneId BOGOTA = ZoneId.of(TIMEZONE);

    private static final Comparator<TopRoute> RANKING = Comparator
            .comparingLong(TopRoute::searches).reversed()
            .thenComparing(TopRoute::routeName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(TopRoute::routeId);

    private final UserCountPort users;
    private final UsageStatsPort usage;
    private final CountPendingFeedbackUseCase pendingFeedback;
    private final EffectiveSuspensionCountPort suspensions;
    private final RouteNamePort routeNames;
    private final Clock clock;

    public GetAdminMetricsService(UserCountPort users, UsageStatsPort usage,
                                  CountPendingFeedbackUseCase pendingFeedback,
                                  EffectiveSuspensionCountPort suspensions, RouteNamePort routeNames,
                                  Clock clock) {
        this.users = users;
        this.usage = usage;
        this.pendingFeedback = pendingFeedback;
        this.suspensions = suspensions;
        this.routeNames = routeNames;
        this.clock = clock;
    }

    @Override
    public AdminMetrics getMetrics() {
        // Un solo instante para todo el panel: el mismo "ahora" decide el dia, la ventana y las suspensiones.
        Instant now = clock.instant();
        LocalDate today = now.atZone(BOGOTA).toLocalDate();
        Instant startOfDay = today.atStartOfDay(BOGOTA).toInstant();
        Instant startOfNextDay = today.plusDays(1).atStartOfDay(BOGOTA).toInstant();
        Instant windowStart = now.minus(TOP_ROUTES_WINDOW_DAYS, ChronoUnit.DAYS);

        return new AdminMetrics(
                users.countAll(),
                usage.countSearchesBetween(startOfDay, startOfNextDay),
                topRoutes(windowStart),
                pendingFeedback.countPending(),
                suspensions.countEffectiveAt(now),
                now.atZone(BOGOTA).toOffsetDateTime(),
                TIMEZONE,
                TOP_ROUTES_WINDOW_DAYS);
    }

    private List<TopRoute> topRoutes(Instant since) {
        List<RouteSearchCount> counts = usage.countSearchesByRoute(since);
        if (counts.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = routeNames.findNamesByIds(
                counts.stream().map(RouteSearchCount::routeId).toList());
        return counts.stream()
                .filter(count -> names.containsKey(count.routeId()))
                .map(count -> new TopRoute(count.routeId(), names.get(count.routeId()), count.searches()))
                .sorted(RANKING)
                .limit(TOP_ROUTES_LIMIT)
                .collect(Collectors.toUnmodifiableList());
    }
}
