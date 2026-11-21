package com.designpatterns.level08.architecture;

import com.designpatterns.level08.payments.application.capabilities.ProviderAdapter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** The module map of ADR-0001, made executable. */
@AnalyzeClasses(packages = "com.designpatterns.level08", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.designpatterns.level08";
    private static final String PAYMENTS = ROOT + ".payments..";

    @ArchTest
    static final ArchRule theCoreDependsOnNoOtherModule = noClasses()
            .that().resideInAPackage(PAYMENTS)
            .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".providers..", ROOT + ".platform..",
                    ROOT + ".merchants..", ROOT + ".audit..", ROOT + ".api..", ROOT + ".config..");

    @ArchTest
    static final ArchRule domainIsPureJava = classes()
            .that().resideInAPackage(ROOT + ".payments.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(ROOT + ".payments.domain..", "java..");

    @ArchTest
    static final ArchRule theCoreIsFrameworkFree = noClasses()
            .that().resideInAPackage(PAYMENTS)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "com.fasterxml..", "java.net.http..");

    @ArchTest
    static final ArchRule providersOnlySeeTheCore = noClasses()
            .that().resideInAPackage(ROOT + ".providers..")
            .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".platform..", ROOT + ".merchants..",
                    ROOT + ".audit..", ROOT + ".api..", ROOT + ".config..");

    @ArchTest
    static final ArchRule providersDontKnowEachOther = slices()
            .matching(ROOT + ".providers.(*)..")
            .should().notDependOnEachOther()
            .ignoreDependency(alwaysTrue(), resideInAPackage(ROOT + ".providers.support.."));

    @ArchTest
    static final ArchRule resilienceIsProviderAgnostic = noClasses()
            .that().resideInAPackage(ROOT + ".platform..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".providers..");

    @ArchTest
    static final ArchRule merchantsAndAuditStayOutOfEachOther = noClasses()
            .that().resideInAnyPackage(ROOT + ".merchants..", ROOT + ".audit..")
            .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".providers..", ROOT + ".api..");

    @ArchTest
    static final ArchRule businessCodeKnowsNoWebhooks = noClasses()
            .that().resideInAPackage(PAYMENTS)
            .should().haveSimpleNameContaining("Webhook")
            .orShould().dependOnClassesThat().resideInAPackage("..webhook..");

    /** Only the gateway is visible from a provider: DTOs, mappers and token caches stay hidden. */
    @ArchTest
    static final ArchRule onlyTheGatewayIsPublic = classes()
            .that().resideInAPackage(ROOT + ".providers.*..")
            .and().resideOutsideOfPackage(ROOT + ".providers.support..")
            .and().arePublic()
            .should().implement(ProviderAdapter.class);

    /** Providers register themselves: the composition root names none of them. */
    @ArchTest
    static final ArchRule theCompositionRootNamesNoProvider = noClasses()
            .that().resideInAPackage(ROOT + ".config..")
            .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".providers.stripe..", ROOT + ".providers.paypal..",
                    ROOT + ".providers.adyen..", ROOT + ".providers.providerx..");
}
