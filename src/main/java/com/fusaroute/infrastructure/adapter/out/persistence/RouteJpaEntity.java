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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.util.List;

/**
 * Fila de la tabla {@code routes} con sus barrios y tarifas. Modelo de persistencia,
 * distinto de {@code domain.model.Route} a proposito. Solo lectura.
 *
 * {@code path_geojson}, {@code created_at} y {@code updated_at} no se mapean: el
 * catalogo publico no usa el trazado (lo usan el mapa y el modo offline).
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

    // El orden de recorrido lo da order_index (parte de la PK), no el orden de insercion.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "route_neighborhoods", joinColumns = @JoinColumn(name = "route_id"))
    @OrderColumn(name = "order_index")
    @Column(name = "neighborhood_name", nullable = false, length = 120)
    @BatchSize(size = 50)
    private List<String> neighborhoods;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false, insertable = false, updatable = false)
    @BatchSize(size = 50)
    private List<FareJpaEntity> fares;

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

    public List<String> getNeighborhoods() {
        return neighborhoods;
    }

    public List<FareJpaEntity> getFares() {
        return fares;
    }
}
