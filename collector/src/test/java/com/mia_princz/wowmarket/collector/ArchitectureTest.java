package com.mia_princz.wowmarket.collector;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

@AnalyzeClasses(packages = "com.mia_princz.wowmarket.collector", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "com.mia_princz.wowmarket.collector";

    @ArchTest
    static final ArchRule layersDependInwardOnly = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain")
            .definedBy(BASE + ".domain..")
            .layer("Application")
            .definedBy(BASE + ".application..")
            .layer("Adapter")
            .definedBy(BASE + ".adapter..")
            .layer("Config")
            .definedBy(BASE + ".config..")
            .whereLayer("Config")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Adapter")
            .mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Application")
            .mayOnlyBeAccessedByLayers("Adapter", "Config")
            .whereLayer("Domain")
            .mayOnlyBeAccessedByLayers("Application", "Adapter", "Config")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainUsesOnlyTheJdk = ArchRuleDefinition.classes()
            .that()
            .resideInAPackage(BASE + ".domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(BASE + ".domain..", "java..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationUsesOnlyDomainAndJdk = ArchRuleDefinition.classes()
            .that()
            .resideInAPackage(BASE + ".application..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(BASE + ".application..", BASE + ".domain..", "java..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule adaptersDoNotUseEachOther = slices().matching(BASE + ".adapter.(*).(*)..")
            .should()
            .notDependOnEachOther()
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noCycles =
            slices().matching(BASE + ".(*)..").should().beFreeOfCycles().allowEmptyShould(true);
}
