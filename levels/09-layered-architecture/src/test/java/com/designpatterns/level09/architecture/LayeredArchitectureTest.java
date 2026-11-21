package com.designpatterns.level09.architecture;

import com.designpatterns.level09.integration.gateway.ProviderAdapter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * FITNESS FUNCTIONS for the layered architecture. In a layered design a dangerous shortcut is often a
 * perfectly legal downward call (a service reaching into StripeGateway, a {@code @Transactional} around a
 * provider call). The structure doesn't stop those, so these rules do: a violating import fails the build.
 */
@AnalyzeClasses(packages = "com.designpatterns.level09", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    private static final String ROOT = "com.designpatterns.level09";

    /** presentation → service → persistence | integration → model. Never up, never skipping service. */
    @ArchTest
    static final ArchRule layersOnlyCallDown = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Presentation").definedBy(ROOT + ".presentation..")
            .layer("Service").definedBy(ROOT + ".service..")
            .layer("Persistence").definedBy(ROOT + ".persistence..")
            .layer("Integration").definedBy(ROOT + ".integration..")
            .layer("Model").definedBy(ROOT + ".model..")
            .layer("Config").definedBy(ROOT + ".config..")

            .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Presentation", "Config")
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service", "Config")
            .whereLayer("Integration").mayOnlyBeAccessedByLayers("Service", "Config")
            .whereLayer("Model").mayOnlyBeAccessedByLayers("Presentation", "Service", "Persistence",
                    "Integration", "Config");

    @ArchTest
    static final ArchRule theModelIsPureJava = classes()
            .that().resideInAPackage(ROOT + ".model..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(ROOT + ".model..", "java..");

    /** Both are "infrastructure" and sit side by side; if they need each other, a service connects them. */
    @ArchTest
    static final ArchRule persistenceDoesNotKnowIntegration = noClasses()
            .that().resideInAPackage(ROOT + ".persistence..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".integration..");

    @ArchTest
    static final ArchRule integrationDoesNotKnowPersistence = noClasses()
            .that().resideInAPackage(ROOT + ".integration..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".persistence..");

    /** Services may use Spring stereotypes, but not HTTP, JSON, JPA or JDBC: those belong to other layers. */
    @ArchTest
    static final ArchRule servicesKnowNoTechnology = noClasses()
            .that().resideInAPackage(ROOT + ".service..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "org.springframework.http..",
                    "org.springframework.jdbc..", "org.springframework.data..", "jakarta.persistence..",
                    "jakarta.servlet..", "com.fasterxml..", "java.net.http..");

    /** Business code talks to "a provider" through the gateway, never to Stripe itself. */
    @ArchTest
    static final ArchRule servicesSeeNoConcreteProvider = noClasses()
            .that().resideInAPackage(ROOT + ".service..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".integration.provider..");

    /**
     * A provider call can take seconds. A transaction around it would hold a DB connection and row locks
     * that long, and a rollback after a successful charge would erase the record of money that moved.
     * Transactions live in persistence, around database work only.
     */
    @ArchTest
    static final ArchRule noTransactionsInServices = noClasses()
            .that().resideInAPackage(ROOT + ".service..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional");

    @ArchTest
    static final ArchRule noTransactionalServiceMethods = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage(ROOT + ".service..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional");

    /** JPA stays an implementation detail of persistence: the model never becomes an @Entity. */
    @ArchTest
    static final ArchRule jpaStaysInPersistence = noClasses()
            .that().resideOutsideOfPackage(ROOT + ".persistence.jpa..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework.data..");

    @ArchTest
    static final ArchRule providersDoNotKnowEachOther = slices()
            .matching(ROOT + ".integration.provider.(*)..")
            .should().notDependOnEachOther();

    /** Inside a provider folder only the gateway is public; DTOs, mappers, clients and translators are not. */
    @ArchTest
    static final ArchRule onlyTheGatewayIsPublic = classes()
            .that().resideInAPackage(ROOT + ".integration.provider..")
            .and().arePublic()
            .should().implement(ProviderAdapter.class);

    @ArchTest
    static final ArchRule controllersLiveInPresentation = classes()
            .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().resideInAPackage(ROOT + ".presentation..");

    /** Providers register themselves (ProviderFactory, translators); the composition root names none. */
    @ArchTest
    static final ArchRule theCompositionRootNamesNoProvider = noClasses()
            .that().resideInAPackage(ROOT + ".config..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".integration.provider..");
}
