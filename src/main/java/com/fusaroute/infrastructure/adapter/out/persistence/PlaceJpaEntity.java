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

import java.util.List;

/**
 * Fila de {@code places} (V21) con sus alias. Modelo de persistencia, distinto de
 * {@code domain.model.Place}. Solo lectura: los lugares entran por migracion.
 *
 * {@code name_normalized} y {@code alias_normalized} no se mapean: son llaves de unicidad que llena la
 * migracion; la comparacion por nombre la hace el dominio.
 */
@Entity
@Table(name = "places")
public class PlaceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "place_aliases", joinColumns = @JoinColumn(name = "place_id"))
    @Column(name = "alias", nullable = false, length = 140)
    @BatchSize(size = 200)
    private List<String> aliases;

    protected PlaceJpaEntity() {
        // Requerido por JPA.
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public List<String> getAliases() {
        return aliases;
    }
}
