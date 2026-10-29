package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.RouteSign;
import com.fusaroute.domain.model.SignSense;
import com.fusaroute.domain.model.SignTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteSignPersistenceMapperTest {

    private static SignLineJpaEntity line(int orderIndex, String text, String bg, String fg) {
        SignLineJpaEntity entity = new SignLineJpaEntity();
        ReflectionTestUtils.setField(entity, "orderIndex", orderIndex);
        ReflectionTestUtils.setField(entity, "lineText", text);
        ReflectionTestUtils.setField(entity, "background", bg);
        ReflectionTestUtils.setField(entity, "foreground", fg);
        return entity;
    }

    private static SignPlateJpaEntity plate(int position, String template, String code, List<SignLineJpaEntity> lines) {
        SignPlateJpaEntity entity = new SignPlateJpaEntity();
        ReflectionTestUtils.setField(entity, "position", position);
        ReflectionTestUtils.setField(entity, "template", template);
        ReflectionTestUtils.setField(entity, "code", code);
        ReflectionTestUtils.setField(entity, "lines", lines);
        return entity;
    }

    private static RouteSignJpaEntity sign(String fieldId, String sense, List<SignPlateJpaEntity> plates) {
        RouteSignJpaEntity entity = new RouteSignJpaEntity();
        ReflectionTestUtils.setField(entity, "fieldId", fieldId);
        ReflectionTestUtils.setField(entity, "sense", sense);
        ReflectionTestUtils.setField(entity, "sourcePhoto", "20260927_150814.jpg");
        ReflectionTestUtils.setField(entity, "evidenceCrop", "20260927_150814.jpg");
        ReflectionTestUtils.setField(entity, "observedOn", LocalDate.of(2026, 9, 27));
        ReflectionTestUtils.setField(entity, "plates", plates);
        return entity;
    }

    @Test
    void traduce_el_letrero_con_sus_placas_y_lineas_en_orden() {
        // Caso real de V11: LC-029, placa en arco con codigo "100".
        List<SignLineJpaEntity> lines = List.of(
                line(1, "LA CLARITA", "#EDB03A", "#1E1E1E"),
                line(0, "CENTRO", "#EDB03A", "#1E1E1E"));
        RouteSignJpaEntity entity = sign("LC-029", null,
                List.of(plate(0, "PLACA_ARCO", "100", lines)));

        RouteSign route = RouteSignPersistenceMapper.toDomain(entity);

        assertThat(route.fieldId()).isEqualTo("LC-029");
        assertThat(route.sense()).isNull();
        assertThat(route.plates()).hasSize(1);
        assertThat(route.plates().get(0).template()).isEqualTo(SignTemplate.PLACA_ARCO);
        assertThat(route.plates().get(0).code()).isEqualTo("100");
        // Ordenado por order_index aunque las entidades llegaron desordenadas.
        assertThat(route.plates().get(0).lines()).extracting("text").containsExactly("CENTRO", "LA CLARITA");
    }

    @Test
    void traduce_el_sentido_cuando_el_letrero_lo_documenta() {
        RouteSignJpaEntity entity = sign("LC-008", "SUBIENDO",
                List.of(plate(0, "TABLA_FRANJAS", null, List.of(line(0, "CAMINO REAL", "#2F8F6A", "#E8F5EE")))));

        RouteSign route = RouteSignPersistenceMapper.toDomain(entity);

        assertThat(route.sense()).isEqualTo(SignSense.SUBIENDO);
    }

    @Test
    void ordena_las_placas_por_posicion_aunque_lleguen_desordenadas() {
        // Caso real de 'Maíz Amarillo': tres placas en el mismo letrero.
        RouteSignJpaEntity entity = sign("LC-018", null, List.of(
                plate(2, "PLACA_RECT", null, List.of(line(0, "éxito", null, null))),
                plate(0, "PLACA_ARCO", "P-15", List.of(line(0, "MAIZ AMARILLO", null, null))),
                plate(1, "PLACA_RECT", null, List.of(line(0, "BATALLON", null, null)))));

        RouteSign route = RouteSignPersistenceMapper.toDomain(entity);

        assertThat(route.plates()).extracting(p -> p.lines().get(0).text())
                .containsExactly("MAIZ AMARILLO", "BATALLON", "éxito");
    }
}
