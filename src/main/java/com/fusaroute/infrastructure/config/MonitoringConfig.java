package com.fusaroute.infrastructure.config;

import com.fusaroute.application.usecase.ListAppLogsService;
import com.fusaroute.application.usecase.ListUsageEventsService;
import com.fusaroute.domain.port.in.ListAppLogsUseCase;
import com.fusaroute.domain.port.in.ListUsageEventsUseCase;
import com.fusaroute.domain.port.out.AppLogQueryPort;
import com.fusaroute.domain.port.out.UsageEventQueryPort;
import com.fusaroute.infrastructure.adapter.out.monitoring.MonitoringTables;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Cableado de los logs y eventos de uso guardados en la base (SCRUM-186). Va aparte de
 * {@link UseCaseConfig} para no tocar un archivo que cambian en paralelo las ramas de la
 * busqueda.
 *
 * <p>{@code @EnableScheduling} es para la purga diaria. Es la primera vez que el
 * proyecto programa algo, y solo hay una tarea: {@code MonitoringDataPurger}.
 */
@Configuration
@EnableScheduling
public class MonitoringConfig {

    /**
     * Mismo {@code DB_SCHEMA} que Flyway e Hibernate (ver application.properties), para
     * que el SQL de JDBC no dependa del {@code search_path} del rol.
     */
    @Bean
    public MonitoringTables monitoringTables(
            @Value("${spring.jpa.properties.hibernate.default_schema:fusaroute}") String schema) {
        return new MonitoringTables(schema);
    }

    @Bean
    public ListAppLogsUseCase listAppLogsUseCase(AppLogQueryPort logs) {
        return new ListAppLogsService(logs);
    }

    @Bean
    public ListUsageEventsUseCase listUsageEventsUseCase(UsageEventQueryPort events) {
        return new ListUsageEventsService(events);
    }
}
