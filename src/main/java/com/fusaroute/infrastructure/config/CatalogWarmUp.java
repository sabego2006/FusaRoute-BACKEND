package com.fusaroute.infrastructure.config;

import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import com.fusaroute.domain.port.in.ListPlacesUseCase;
import com.fusaroute.domain.port.out.TrafficSectorRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Deja el catalogo ya cargado en memoria cuando la aplicacion termina de arrancar (SCRUM-192).
 *
 * <p>Sin esto, la primera rafaga despues de un despliegue o de que Render despierte el servicio paga la
 * lectura de las rutas y, sobre todo, el conteo de rutas por lugar (el calculo mas caro de las lecturas
 * publicas) con la CPU compartida entre todos los que llegaron a la vez. Hacerlo aqui lo paga el arranque,
 * una sola vez y sin usuarios esperando.
 *
 * <p>Va en {@link ApplicationReadyEvent} porque ahi Flyway ya migro. Es una optimizacion, no un requisito:
 * si falla (la base no responde), se registra y la aplicacion arranca igual; la primera peticion lo cargara.
 */
@Component
public class CatalogWarmUp {

    private static final Logger log = LoggerFactory.getLogger(CatalogWarmUp.class);

    private final ListActiveRoutesUseCase listActiveRoutes;
    private final ListPlacesUseCase listPlaces;
    private final TrafficSectorRepositoryPort trafficSectors;

    public CatalogWarmUp(ListActiveRoutesUseCase listActiveRoutes, ListPlacesUseCase listPlaces,
                         TrafficSectorRepositoryPort trafficSectors) {
        this.listActiveRoutes = listActiveRoutes;
        this.listPlaces = listPlaces;
        this.trafficSectors = trafficSectors;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            long startedAt = System.nanoTime();
            int routes = listActiveRoutes.listActive().size();
            int places = listPlaces.listPlaces().size();
            int sectors = trafficSectors.findAll().size();
            log.info("Catalogo precargado en memoria: {} rutas, {} lugares y {} sectores en {} ms",
                    routes, places, sectors, (System.nanoTime() - startedAt) / 1_000_000);
        } catch (RuntimeException e) {
            log.warn("No se pudo precargar el catalogo; se cargara con la primera peticion: {}", e.getMessage());
        }
    }
}
