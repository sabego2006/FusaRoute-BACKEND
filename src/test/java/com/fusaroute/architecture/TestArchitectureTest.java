package com.fusaroute.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Vela por que los tests de dominio y aplicacion sigan siendo unitarios y
 * rapidos: nada de Testcontainers, Spring ni JPA en esas capas. El beneficio
 * concreto de hexagonal es poder probar la logica de negocio sin levantar nada;
 * esta regla lo convierte en build rojo si alguien lo rompe.
 */
@AnalyzeClasses(packages = "com.fusaroute", importOptions = ImportOption.OnlyIncludeTests.class)
class TestArchitectureTest {

    @ArchTest
    public void tests_de_dominio_y_aplicacion_no_usan_frameworks_pesados(JavaClasses classes) {
        noClasses()
            .that().resideInAnyPackage("com.fusaroute.domain..", "com.fusaroute.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.testcontainers..",
                    "org.springframework..",
                    "jakarta.persistence..")
            .allowEmptyShould(true)
            .check(classes);
    }
}
