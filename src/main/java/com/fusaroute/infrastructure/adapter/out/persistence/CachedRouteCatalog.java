package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteSuspension;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Catalogo de rutas en memoria (SCRUM-192). Es el {@link RouteRepositoryPort} que reciben los casos de uso;
 * {@link RoutePersistenceAdapter} queda como el lector de la base que lo alimenta.
 *
 * <p><b>Por que existe.</b> Cada lectura publica (catalogo, detalle, lugares, busqueda) traia las 34 rutas
 * con Hibernate y volvia a parsear sus trazados GeoJSON. En la instancia gratis de Render (0,1 CPU) eso
 * dejaba a 30 lectores simultaneos con un p95 de 17,9 s. El catalogo solo cambia cuando el administrador
 * escribe (RF-11, RF-12), asi que se lee una vez y se reutiliza.
 *
 * <p><b>Por que no sirve datos viejos.</b>
 * <ul>
 *   <li>Toda escritura del administrador sobre rutas o suspensiones llama a {@link #invalidateAfterCommit()}:
 *       la siguiente lectura despues del commit vuelve a la base.</li>
 *   <li>La invalidacion sube un numero de version. Una lectura que empezo ANTES del commit y termina
 *       despues no puede guardar su foto vieja: solo se guarda si la version no cambio mientras leia.</li>
 *   <li>De la suspension no se guarda el resultado ("esta ruta esta suspendida") sino las ventanas sin
 *       levantar. Cada lectura las evalua contra el reloj con {@link RouteSuspension#isEffectiveAt}, la
 *       misma regla de la consulta SQL: la ruta desaparece en {@code startsAt} y vuelve sola en
 *       {@code endsAt}, sin releer la base.</li>
 *   <li>{@link #TTL_MILLIS} es solo una red de seguridad para cambios hechos por fuera de la aplicacion
 *       (un UPDATE a mano en la base): la aplicacion nunca depende de el.</li>
 * </ul>
 *
 * <p>La cache vive en el proceso. Sirve mientras haya una sola instancia del backend, que es lo que da el
 * plan gratis de Render; con varias instancias la invalidacion tendria que viajar entre ellas.
 */
@Component
@Primary
public class CachedRouteCatalog implements RouteRepositoryPort {

    static final long TTL_MILLIS = 10 * 60 * 1000L;

    private final RoutePersistenceAdapter loader;
    private final Clock clock;

    private final AtomicLong version = new AtomicLong();
    private final Object loadLock = new Object();
    private volatile Snapshot snapshot;

    public CachedRouteCatalog(RoutePersistenceAdapter loader, Clock clock) {
        this.loader = loader;
        this.clock = clock;
    }

    /**
     * Devuelve LA MISMA lista mientras ni el catalogo ni el conjunto de rutas suspendidas cambien. Quien
     * derive algo caro de ella (los lugares con servicio) puede reutilizarlo comparando por identidad.
     */
    @Override
    public List<Route> findAll() {
        Snapshot current = current();
        Set<Long> suspended = current.suspendedAt(clock.instant());
        Presented last = current.presented;
        if (last != null && last.suspended.equals(suspended)) {
            return last.routes;
        }
        List<Route> routes = suspended.isEmpty()
                ? current.routes
                : current.routes.stream().map(route -> RoutePersistenceAdapter.presented(route, suspended)).toList();
        current.presented = new Presented(suspended, routes);
        return routes;
    }

    @Override
    public Optional<Route> findById(Long id) {
        Snapshot current = current();
        Route route = current.byId.get(id);
        if (route == null) {
            return Optional.empty();
        }
        return Optional.of(RoutePersistenceAdapter.presented(route, current.suspendedAt(clock.instant())));
    }

    /** Descarta la foto ya: la siguiente lectura va a la base. */
    public void invalidate() {
        version.incrementAndGet();
    }

    /**
     * Descarta la foto cuando la transaccion en curso haga commit. Invalidar antes del commit no sirve: otra
     * peticion releeria la base, veria todavia lo viejo y lo dejaria guardado. Sin transaccion activa
     * invalida de inmediato.
     */
    public void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate();
                }
            });
        } else {
            invalidate();
        }
    }

    private Snapshot current() {
        Snapshot existing = snapshot;
        if (isFresh(existing)) {
            return existing;
        }
        // Un solo lector va a la base; los demas esperan y reutilizan su foto. Con 0,1 CPU, treinta
        // cargas simultaneas del catalogo al arrancar son justo el atasco que esta clase evita.
        synchronized (loadLock) {
            existing = snapshot;
            if (isFresh(existing)) {
                return existing;
            }
            long versionBeforeRead = version.get();
            List<Route> routes = loader.loadAll();
            // Si la relectura (por tiempo de vida) trae exactamente lo mismo, se conserva la lista anterior:
            // quien guarda algo calculado sobre ella (los lugares con servicio) no tiene que rehacerlo.
            if (existing != null && existing.routes.equals(routes)) {
                routes = existing.routes;
            }
            Snapshot loaded = new Snapshot(routes, loader.loadOpenSuspensions(),
                    versionBeforeRead, clock.millis());
            if (version.get() == versionBeforeRead) {
                snapshot = loaded;
            }
            return loaded;
        }
    }

    private boolean isFresh(Snapshot candidate) {
        return candidate != null
                && candidate.version == version.get()
                && Math.abs(clock.millis() - candidate.loadedAt) < TTL_MILLIS;
    }

    private static final class Snapshot {
        private final List<Route> routes;
        private final Map<Long, Route> byId;
        private final List<RouteSuspension> openSuspensions;
        private final long version;
        private final long loadedAt;
        private volatile Presented presented;

        Snapshot(List<Route> routes, List<RouteSuspension> openSuspensions, long version, long loadedAt) {
            // List.copyOf devuelve la misma instancia si ya es inmutable: la identidad se conserva.
            this.routes = List.copyOf(routes);
            this.openSuspensions = List.copyOf(openSuspensions);
            Map<Long, Route> index = new HashMap<>();
            this.routes.forEach(route -> index.put(route.id(), route));
            this.byId = Map.copyOf(index);
            this.version = version;
            this.loadedAt = loadedAt;
        }

        Set<Long> suspendedAt(Instant now) {
            if (openSuspensions.isEmpty()) {
                return Set.of();
            }
            return openSuspensions.stream().filter(suspension -> suspension.isEffectiveAt(now))
                    .map(RouteSuspension::routeId).collect(Collectors.toUnmodifiableSet());
        }
    }

    private record Presented(Set<Long> suspended, List<Route> routes) {
    }
}
