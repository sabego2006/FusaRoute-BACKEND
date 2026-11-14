package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.RouteConflictException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.exception.SuspensionNotFoundException;
import com.fusaroute.domain.model.AdminRoute;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteSuspension;
import com.fusaroute.domain.model.SuspensionFilter;
import com.fusaroute.domain.model.SuspensionState;
import com.fusaroute.domain.model.SuspensionView;
import com.fusaroute.domain.port.in.CreateRouteSuspensionCommand;
import com.fusaroute.domain.port.in.CreateRouteSuspensionUseCase;
import com.fusaroute.domain.port.in.LiftRouteSuspensionUseCase;
import com.fusaroute.domain.port.in.ListRouteSuspensionsUseCase;
import com.fusaroute.domain.port.out.AdminRouteRepositoryPort;
import com.fusaroute.domain.port.out.RouteSuspensionRepositoryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Suspension temporal de rutas del administrador (RF-12, SCRUM-25): crear, listar y reactivar a mano.
 *
 * <p>No toca {@code routes.status}: la suspension vive en su propia tabla y la ruta vuelve sola cuando la
 * ventana se cierra (sin scheduler). Todo se decide con el {@link Clock} inyectado.
 */
public class RouteSuspensionService
        implements CreateRouteSuspensionUseCase, ListRouteSuspensionsUseCase, LiftRouteSuspensionUseCase {

    private final RouteSuspensionRepositoryPort suspensions;
    private final AdminRouteRepositoryPort routes;
    private final Clock clock;

    public RouteSuspensionService(RouteSuspensionRepositoryPort suspensions, AdminRouteRepositoryPort routes,
                                  Clock clock) {
        this.suspensions = suspensions;
        this.routes = routes;
        this.clock = clock;
    }

    /** Orden: ruta (404), datos (400), estado de la ruta y solapes (409). */
    @Override
    public SuspensionView create(CreateRouteSuspensionCommand command) {
        Instant now = clock.instant();
        AdminRoute route = routes.findById(command.routeId()).orElseThrow(RouteNotFoundException::new);
        RouteSuspension draft = RouteSuspension.open(route.id(), route.name(), command.startsAt(), command.endsAt(),
                command.reason(), now);
        // INACTIVA es el soft delete: no tiene sentido ponerle una ventana a una ruta que ya esta fuera.
        if (route.status() == RouteStatus.INACTIVA) {
            throw new RouteConflictException("La ruta esta inactiva: no se puede suspender");
        }
        boolean overlaps = suspensions.findNotLiftedByRoute(route.id()).stream()
                .anyMatch(other -> other.overlaps(draft.startsAt(), draft.endsAt()));
        if (overlaps) {
            throw new RouteConflictException("La ventana se solapa con otra suspension de esta ruta");
        }
        return view(suspensions.save(draft, command.adminId()), now);
    }

    @Override
    public List<SuspensionView> list(SuspensionFilter filter) {
        Instant now = clock.instant();
        return suspensions.findAll().stream()
                .map(suspension -> view(suspension, now))
                .filter(view -> filter.accepts(view.state()))
                .sorted(ORDER)
                .toList();
    }

    /** 404 si no existe; 409 si ya termino o ya estaba levantada. Una proxima se puede levantar. */
    @Override
    public SuspensionView lift(Long suspensionId) {
        Instant now = clock.instant();
        RouteSuspension existing = suspensions.findById(suspensionId).orElseThrow(SuspensionNotFoundException::new);
        if (existing.liftedAt() != null) {
            throw new RouteConflictException("La suspension ya fue levantada");
        }
        if (!existing.canBeLiftedAt(now)) {
            throw new RouteConflictException("La suspension ya termino");
        }
        return view(suspensions.markLifted(suspensionId, now), now);
    }

    private static SuspensionView view(RouteSuspension suspension, Instant now) {
        return new SuspensionView(suspension, suspension.stateAt(now));
    }

    /**
     * Vigentes (la que termina antes, primero), proximas (la que empieza antes, primero) y pasadas (la mas
     * reciente primero); id desempata para que el orden no dependa de la base.
     */
    private static final Comparator<SuspensionView> ORDER = Comparator
            .comparingInt((SuspensionView v) -> group(v.state()))
            .thenComparing(RouteSuspensionService::withinGroup)
            .thenComparing(v -> v.suspension().id(), Comparator.nullsLast(Comparator.naturalOrder()));

    private static int group(SuspensionState state) {
        return switch (state) {
            case ACTIVE -> 0;
            case UPCOMING -> 1;
            case ENDED, LIFTED -> 2;
        };
    }

    /** Clave de orden dentro del grupo, ya en el sentido correcto (las pasadas se invierten). */
    private static int withinGroup(SuspensionView a, SuspensionView b) {
        Instant keyA = key(a);
        Instant keyB = key(b);
        return group(a.state()) == 2 ? keyB.compareTo(keyA) : keyA.compareTo(keyB);
    }

    private static Instant key(SuspensionView view) {
        return view.state() == SuspensionState.ACTIVE ? view.suspension().endsAt() : view.suspension().startsAt();
    }
}
