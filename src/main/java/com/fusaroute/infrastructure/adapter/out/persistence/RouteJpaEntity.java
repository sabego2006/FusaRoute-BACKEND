package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

/**
 * Fila de la tabla {@code routes} con sus barrios y tarifas. Modelo de persistencia,
 * distinto de {@code domain.model.Route} a proposito. Solo lectura.
 *
 * {@code created_at} y {@code updated_at} no se mapean: nada en el dominio los usa hoy.
 *
 * {@code path_geojson} SI se mapea (como texto JSON crudo; {@code RoutePersistenceMapper}
 * lo parsea a {@code List<Coordinate>}): aunque el catalogo publico no lo necesita, desde
 * SCRUM-47/48-50 el buscador de ruta (RF-04) si lo usa, via {@code route.path()} hacia
 * {@code TravelTimePort}.
 *
 * Las dos colecciones son LAZY con {@code @BatchSize}: dos listas (bags) no se pueden
 * traer con JOIN FETCH a la vez, y sin batch serian 1+2N consultas contra una base remota.
 */
@Entity
@Table(name = "routes")
public class RouteJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 140)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_type", nullable = false, length = 20)
    private RouteType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RouteStatus status;

    // Mapa order_index -> nombre, y no List con @OrderColumn: la semilla deja huecos a
    // proposito (la ruta "100" no tiene indice 2: tramo sin datos) y Hibernate rellena
    // los huecos de una lista con null. El mapa los conserva; el mapper ordena por indice.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "route_neighborhoods", joinColumns = @JoinColumn(name = "route_id"))
    @MapKeyColumn(name = "order_index")
    @Column(name = "neighborhood_name", nullable = false, length = 120)
    @BatchSize(size = 50)
    private Map<Integer, String> neighborhoods;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false, insertable = false, updatable = false)
    @BatchSize(size = 50)
    private List<FareJpaEntity> fares;

    // Texto JSON crudo (LineString de GeoJSON), NULL en las rutas sin trazado todavia.
    // String y no un objeto propio: el unico consumidor es RoutePersistenceMapper, que
    // ya lo parsea a List<Coordinate> del lado del dominio.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "path_geojson")
    private String pathGeoJson;

    protected RouteJpaEntity() {
        // Requerido por JPA.
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public RouteType getType() {
        return type;
    }

    public RouteStatus getStatus() {
        return status;
    }

    public Map<Integer, String> getNeighborhoods() {
        return neighborhoods;
    }

    public List<FareJpaEntity> getFares() {
        return fares;
    }

    public String getPathGeoJson() {
        return pathGeoJson;
    }
}
