package com.bank.clients;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

/** Architecture rules checked on every build (ADR-005, ADR-024, SPEC §2). */
class ArchitectureTest {

    private static final String ROOT = "com.bank.clients";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    /** Dependencies point inwards: infrastructure → application → domain. */
    @Test
    void layersOnlyDependInwards() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy(ROOT + ".domain..")
                .layer("Application").definedBy(ROOT + ".application..")
                .layer("Infrastructure").definedBy(ROOT + ".infrastructure..")
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
                .check(CLASSES);
    }

    /** Principle 1: the domain is plain Java. */
    @Test
    void domainDoesNotDependOnFrameworks() {
        noClasses().that().resideInAPackage(ROOT + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "org.hibernate..", "jakarta.validation..",
                        "tools.jackson..", "com.fasterxml.jackson..", "lombok..", "org.slf4j..")
                .check(CLASSES);
    }

    /** ADR-024 (D): dependencies come through constructors. */
    @Test
    void noFieldInjection() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(CLASSES);
    }

    /** ADR-024 (S): controllers translate HTTP and delegate to use cases, never to persistence. */
    @Test
    void controllersDoNotUsePersistence() {
        noClasses().that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".infrastructure.persistence..")
                .orShould().dependOnClassesThat().resideInAPackage(ROOT + ".domain.port..")
                .check(CLASSES);
    }

    /** Use cases talk to the outside only through domain ports. */
    @Test
    void useCasesDoNotDependOnSpringDataOrMessaging() {
        noClasses().that().resideInAPackage(ROOT + ".application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.data..", "org.springframework.amqp..", "jakarta.persistence..",
                        "org.springframework.web..")
                .check(CLASSES);
    }

    @Test
    void loggingGoesThroughSlf4j() {
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(CLASSES);
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(CLASSES);
    }
}
