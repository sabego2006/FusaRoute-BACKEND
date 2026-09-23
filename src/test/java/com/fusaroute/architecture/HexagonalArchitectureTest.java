package com.fusaroute.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.library.Architectures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.fusaroute", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    public void capas_apuntan_hacia_adentro(JavaClasses classes) {
        // optionalLayer, no layer: domain/ y application/ hoy solo tienen el .gitkeep
        // (SCRUM-163 borró las clases Empty.java que sostenían el gate de JaCoCo).
        // Con layer() una capa vacía es en sí misma una violación; optionalLayer()
        // deja la regla activa y lista para cuando llegue la primera clase real.
        Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .optionalLayer("Domain").definedBy("com.fusaroute.domain..")
            .optionalLayer("Application").definedBy("com.fusaroute.application..")
            .layer("Infrastructure").definedBy("com.fusaroute.infrastructure..")
            .whereLayer("Domain").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .check(classes);
    }

    @ArchTest
    public void dominio_no_conoce_frameworks(JavaClasses classes) {
        noClasses()
            .that().resideInAPackage("com.fusaroute.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "com.fasterxml.jackson..")
            .allowEmptyShould(true)
            .check(classes);
    }
}
