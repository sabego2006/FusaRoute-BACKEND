package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.InvalidRouteException;
import com.fusaroute.domain.exception.RouteConflictException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.AdminRoute;
import com.fusaroute.domain.model.FareChange;
import com.fusaroute.domain.model.RouteDraft;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.port.in.CreateRouteUseCase;
import com.fusaroute.domain.port.in.DeactivateRouteUseCase;
import com.fusaroute.domain.port.in.GetAdminRouteUseCase;
import com.fusaroute.domain.port.in.ListAllRoutesUseCase;
import com.fusaroute.domain.port.in.RestoreRouteUseCase;
import com.fusaroute.domain.port.in.UpdateRouteUseCase;
import com.fusaroute.domain.port.out.AdminRouteRepositoryPort;

import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * CRUD de rutas del administrador (RF-11, SCRUM-24). Las respuestas llevan solo las
 * tarifas vigentes a hoy (America/Bogota, por el {@link Clock}), igual que el catalogo.
 */
public class AdminRouteService implements ListAllRoutesUseCase, GetAdminRouteUseCase, CreateRouteUseCase,
        UpdateRouteUseCase, DeactivateRouteUseCase, RestoreRouteUseCase {

    private final AdminRouteRepositoryPort routes;
    private final Clock clock;

    public AdminRouteService(AdminRouteRepositoryPort routes, Clock clock) {
        this.routes = routes;
        this.clock = clock;
    }

    @Override
    public List<AdminRoute> listAll() {
        LocalDate today = LocalDate.now(clock);
        Collator collator = Collator.getInstance(Locale.forLanguageTag("es-CO"));
        return routes.findAll().stream()
                .map(route -> route.withCurrentFares(today))
                .sorted((a, b) -> collator.compare(a.name(), b.name()))
                .toList();
    }

    @Override
    public AdminRoute getById(Long id) {
        return find(id).withCurrentFares(LocalDate.now(clock));
    }

    @Override
    public AdminRoute create(RouteDraft draft) {
        validate(draft, null);
        LocalDate today = LocalDate.now(clock);
        return routes.create(draft, FareChange.toRows(draft.fares(), today)).withCurrentFares(today);
    }

    @Override
    public AdminRoute update(Long id, RouteDraft draft) {
        AdminRoute existing = find(id);
        validate(draft, id);
        LocalDate today = LocalDate.now(clock);
        FareChange change = FareChange.between(existing.withCurrentFares(today).fares(), draft.fares(), today);
        return routes.update(id, draft, change).withCurrentFares(today);
    }

    @Override
    public void deactivate(Long id) {
        AdminRoute existing = find(id);
        if (existing.status() != RouteStatus.INACTIVA) {
            routes.setStatus(id, RouteStatus.INACTIVA);
        }
    }

    @Override
    public AdminRoute restore(Long id) {
        AdminRoute existing = find(id);
        if (existing.status() != RouteStatus.INACTIVA) {
            throw new RouteConflictException("La ruta no esta inactiva");
        }
        routes.setStatus(id, RouteStatus.ACTIVA);
        return getById(id);
    }

    private AdminRoute find(Long id) {
        return routes.findById(id).orElseThrow(RouteNotFoundException::new);
    }

    /** Formato primero (400); unicidad despues (409), para no revelar nombres ocupados con datos mal formados. */
    private void validate(RouteDraft draft, Long excludeId) {
        List<FieldViolation> violations = draft.violations();
        if (!violations.isEmpty()) {
            throw new InvalidRouteException(violations);
        }
        if (routes.existsByName(draft.name(), excludeId)) {
            throw new RouteConflictException("Ya existe una ruta con ese nombre");
        }
        if (draft.signFile() != null && routes.existsBySignFile(draft.signFile(), excludeId)) {
            throw new RouteConflictException("Ya existe una ruta con ese letrero");
        }
    }
}
