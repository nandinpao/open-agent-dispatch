package com.opensocket.aievent.core.iam.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.opensocket.aievent.core")
class IamModuleBoundaryArchitectureTest {

    @ArchTest
    static final ArchRule security_contracts_are_framework_free = noClasses()
            .that().resideInAPackage("..iam.security.contract..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "org.mybatis..", "jakarta.persistence..",
                    "..iam.identity..", "..iam.organization..", "..iam.rbac..",
                    "..iam.authentication..", "..iam.token..", "..iam.persistence..", "..iam.api..");

    @ArchTest
    static final ArchRule iam_domain_does_not_depend_on_adapters = noClasses()
            .that().resideInAnyPackage(
                    "..iam.identity..", "..iam.organization..", "..iam.rbac..",
                    "..iam.authentication..", "..iam.token..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..iam.persistence..", "..iam.api..", "..iam.assembly..",
                    "org.mybatis..", "org.springframework.web..");

    @ArchTest
    static final ArchRule authentication_does_not_depend_on_rbac_implementation = noClasses()
            .that().resideInAPackage("..iam.authentication..")
            .should().dependOnClassesThat().resideInAPackage("..iam.rbac..");

    @ArchTest
    static final ArchRule rbac_does_not_depend_on_dispatch_business_modules = noClasses()
            .that().resideInAPackage("..iam.rbac..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..agent..", "..task..", "..execution..", "..adapter.action..", "..incident..");

    @ArchTest
    static final ArchRule new_iam_modules_do_not_depend_on_legacy_identity = noClasses()
            .that().resideInAPackage("..iam..")
            .should().dependOnClassesThat().resideInAPackage("com.opensocket.aievent.core.identity..");

    @ArchTest
    static final ArchRule legacy_identity_does_not_depend_on_new_iam_implementations = noClasses()
            .that().resideInAPackage("com.opensocket.aievent.core.identity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..iam.identity..", "..iam.organization..", "..iam.rbac..",
                    "..iam.authentication..", "..iam.token..", "..iam.persistence..", "..iam.api..");

    @ArchTest
    static final ArchRule iam_api_does_not_access_persistence_implementation = noClasses()
            .that().resideInAPackage("..iam.api..")
            .should().dependOnClassesThat().resideInAnyPackage("..iam.persistence..", "org.mybatis..");
}
