package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsageEventTest {

    private static final Instant AHORA = Instant.parse("2026-11-08T12:00:00Z");

    @Test
    void un_evento_valido_queda_sin_id_hasta_persistirse() {
        UsageEvent evento = UsageEvent.of(AHORA, UsageEventType.BOARDED, null, null, null,
                SearchOutcome.FOUND, null, null, false);

        assertThat(evento.id()).isNull();
        assertThat(evento.routeId()).isNull();
    }

    @Test
    void exige_fecha_tipo_y_resultado() {
        assertThatThrownBy(() -> UsageEvent.of(null, UsageEventType.SEARCH, "a", "b", 1L,
                SearchOutcome.FOUND, null, 10, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UsageEvent.of(AHORA, null, "a", "b", 1L,
                SearchOutcome.FOUND, null, 10, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UsageEvent.of(AHORA, UsageEventType.SEARCH, "a", "b", 1L,
                null, null, 10, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void la_latencia_no_puede_ser_negativa() {
        assertThatThrownBy(() -> UsageEvent.of(AHORA, UsageEventType.SEARCH, "a", "b", 1L,
                SearchOutcome.FOUND, EtaSource.ESTIMATED, -1, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
