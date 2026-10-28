package com.fusaroute.infrastructure.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.time.LocalTime;
import java.util.Set;

/**
 * Fila de {@code traffic_sectors} con los barrios que abarca (RF-09). Modelo de
 * persistencia, distinto de {@code domain.model.TrafficSector}. Solo lectura.
 *
 * {@code created_at} y {@code updated_at} no se mapean: nada en el dominio los usa.
 *
 * Los barrios son un {@code Set} y no una lista ordenada porque el sector no tiene
 * recorrido: son los barrios que caen dentro, sin orden entre ellos.
 */
@Entity
@Table(name = "traffic_sectors")
public class TrafficSectorJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "traffic_sector_neighborhoods", joinColumns = @JoinColumn(name = "sector_id"))
    @Column(name = "neighborhood_name", nullable = false, length = 120)
    @BatchSize(size = 50)
    private Set<String> neighborhoods;

    protected TrafficSectorJpaEntity() {
        // Requerido por JPA.
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Set<String> getNeighborhoods() {
        return neighborhoods;
    }
}
