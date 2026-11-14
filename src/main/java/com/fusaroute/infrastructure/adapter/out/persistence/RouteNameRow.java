package com.fusaroute.infrastructure.adapter.out.persistence;

/** Proyeccion de {@code routes}: solo id y nombre, para el ranking del panel de metricas (SCRUM-26). */
interface RouteNameRow {

    Long getId();

    String getName();
}
