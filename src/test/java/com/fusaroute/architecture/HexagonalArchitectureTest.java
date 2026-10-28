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
        // optionalLayer, no layer: por si en algun momento una capa queda sin
        // clases (con layer() una capa vacia es en si misma una violacion).
        //
        // La dependencia apunta hacia adentro: el dominio es la capa mas interna,
        // asi que puede ser accedido por Application e Infrastructure, pero no al
        // reves. Se enumera quien PUEDE acceder a cada capa; que el dominio no
        // salga hacia afuera lo garantiza que Application/Infrastructure sean los
        // unicos accesos permitidos, mas la regla dominio_no_conoce_frameworks.
        Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .optionalLayer("Domain").definedBy("com.fusaroute.domain..")
            .optionalLayer("Application").definedBy("com.fusaroute.application..")
            .layer("Infrastructure").definedBy("com.fusaroute.infrastructure..")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
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
