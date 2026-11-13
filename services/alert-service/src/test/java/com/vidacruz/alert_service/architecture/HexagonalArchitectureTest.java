package com.vidacruz.alert_service.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.vidacruz.alert_service", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String BASE = "com.vidacruz.alert_service";

    @ArchTest
    static final ArchRule dominioNoDependeDeOtrasCapas = noClasses()
            .that().resideInAPackage(BASE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + ".application..", BASE + ".infrastructure..");

    @ArchTest
    static final ArchRule aplicacionNoDependeDeInfraestructura = noClasses()
            .that().resideInAPackage(BASE + ".application..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".infrastructure..");

    @ArchTest
    static final ArchRule dominioSinFrameworks = noClasses()
            .that().resideInAPackage(BASE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "reactor..", "org.apache.kafka..",
                    "com.fasterxml.jackson..", "io.github.resilience4j..");

    @ArchTest
    static final ArchRule entradaSoloConoceCasosDeUso = noClasses()
            .that().resideInAPackage(BASE + ".infrastructure.adapter.in..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".application.service..");

    @ArchTest
    static final ArchRule adaptadoresNoSeConocen = noClasses()
            .that().resideInAPackage(BASE + ".infrastructure.adapter.in..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".infrastructure.adapter.out..");
}
