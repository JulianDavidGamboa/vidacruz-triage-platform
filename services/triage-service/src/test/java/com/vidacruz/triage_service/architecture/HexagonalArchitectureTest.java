package com.vidacruz.triage_service.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Hace cumplir la arquitectura hexagonal en cada build:
 * dominio ← aplicación ← infraestructura (las dependencias solo apuntan hacia adentro).
 */
@AnalyzeClasses(packages = "com.vidacruz.triage_service", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule dominioNoDependeDeNadaExterno = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..", "..infrastructure..",
                    "org.springframework..", "reactor..", "jakarta..",
                    "com.fasterxml..", "io.r2dbc..", "org.apache.kafka..", "com.vidacruz.events_contract..");

    @ArchTest
    static final ArchRule aplicacionNoDependeDeInfraestructura = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule adaptadoresDeEntradaNoUsanAdaptadoresDeSalida = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..");

    @ArchTest
    static final ArchRule adaptadoresDeSalidaNoUsanAdaptadoresDeEntrada = noClasses()
            .that().resideInAPackage("..adapter.out..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.in..");

    @ArchTest
    static final ArchRule adaptadoresDeEntradaSoloConocenPuertosNoImplementaciones = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..application.service..");

    /** Los puertos son interfaces; los objetos de entrada/salida de los casos de uso (Command, Vista) son records. */
    @ArchTest
    static final ArchRule puertosSonInterfaces = classes()
            .that().resideInAPackage("..application.port..")
            .and().haveSimpleNameNotEndingWith("Command")
            .and().haveSimpleNameNotEndingWith("Vista")
            .should().beInterfaces();
}
