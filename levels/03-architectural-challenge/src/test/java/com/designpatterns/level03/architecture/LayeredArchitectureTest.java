package com.designpatterns.level03.architecture;

import com.designpatterns.level03.integration.gateway.PaymentGateway;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTag;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The Level 2 layering rules, applied to this level. A violating import fails the rule.
 *
 * <pre>
 * presentation → service → persistence | integration → model        (config wires everything)
 * </pre>
 */
@AnalyzeClasses(packages = "com.designpatterns.level03", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    private static final String ROOT = "com.designpatterns.level03";

    /** Dependencies only point down: never up, never skipping the service layer. */
    @ArchTest
    @ArchTag("challenge")
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

    /** Business code talks to "a provider" through the gateway contract, never to one provider's class. */
    @ArchTest
    @ArchTag("challenge")
    static final ArchRule servicesSeeNoConcreteProvider = noClasses()
            .that().resideInAPackage(ROOT + ".service..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".integration.provider..");

    @ArchTest
    static final ArchRule theModelIsPureJava = classes()
            .that().resideInAPackage(ROOT + ".model..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(ROOT + ".model..", "java..");

    @ArchTest
    static final ArchRule persistenceDoesNotKnowIntegration = noClasses()
            .that().resideInAPackage(ROOT + ".persistence..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".integration..");

    @ArchTest
    static final ArchRule integrationDoesNotKnowPersistence = noClasses()
            .that().resideInAPackage(ROOT + ".integration..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".persistence..");

    /** Services may use Spring stereotypes, but not HTTP, JSON, JPA or JDBC. */
    @ArchTest
    static final ArchRule servicesKnowNoTechnology = noClasses()
            .that().resideInAPackage(ROOT + ".service..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "org.springframework.http..",
                    "org.springframework.jdbc..", "org.springframework.data..", "jakarta.persistence..",
                    "jakarta.servlet..", "com.fasterxml..", "java.net.http..");

    /** A transaction must never stay open across a provider call. */
    @ArchTest
    static final ArchRule noTransactionsInServices = noClasses()
            .that().resideInAPackage(ROOT + ".service..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional");

    @ArchTest
    static final ArchRule noTransactionalServiceMethods = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage(ROOT + ".service..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional");

    @ArchTest
    static final ArchRule jpaStaysInPersistence = noClasses()
            .that().resideOutsideOfPackage(ROOT + ".persistence.jpa..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework.data..");

    @ArchTest
    static final ArchRule providersDoNotKnowEachOther = slices()
            .matching(ROOT + ".integration.provider.(*)..")
            .should().notDependOnEachOther();

    /** Inside a provider folder only the gateway is public; DTOs and mappers are not. */
    @ArchTest
    static final ArchRule onlyTheGatewayIsPublic = classes()
            .that().resideInAPackage(ROOT + ".integration.provider..")
            .and().arePublic()
            .should().implement(PaymentGateway.class);

    @ArchTest
    static final ArchRule controllersLiveInPresentation = classes()
            .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().resideInAPackage(ROOT + ".presentation..");

    @ArchTest
    static final ArchRule theCompositionRootNamesNoProvider = noClasses()
            .that().resideInAPackage(ROOT + ".config..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".integration.provider..");
}
