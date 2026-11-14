package com.az.accounts;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.az.accounts", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // Full package names on purpose: "..domain.." would also match Spring's own
    // org.springframework.data.domain package and report false violations.
    private static final String API = "com.az.accounts.api..";
    private static final String SERVICE = "com.az.accounts.service..";
    private static final String DOMAIN = "com.az.accounts.domain..";
    private static final String REPOSITORY = "com.az.accounts.repository..";

    @ArchTest
    static final ArchRule webLayerDoesNotTouchEntitiesOrRepositories =
            noClasses()
                    .that()
                    .resideInAPackage(API)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(DOMAIN, REPOSITORY);

    @ArchTest
    static final ArchRule servicesDoNotDependOnTheWebLayer =
            noClasses()
                    .that()
                    .resideInAPackage(SERVICE)
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(API);

    @ArchTest
    static final ArchRule domainDependsOnNothingAbove =
            noClasses()
                    .that()
                    .resideInAPackage(DOMAIN)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(SERVICE, API, REPOSITORY);
}
