package com.opensocket.aievent.core.resourceaccess.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.opensocket.aievent.core")
class ResourceAccessModuleArchitectureTest {
    @ArchTest static final ArchRule contracts_are_framework_and_business_entity_neutral = noClasses()
            .that().resideInAPackage("..resourceaccess.contract..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "org.mybatis..", "..task..", "..a2a..", "..issuetracking..", "..agent..", "..execution..");

    @ArchTest static final ArchRule core_does_not_depend_on_adapters_or_business_modules = noClasses()
            .that().resideInAPackage("..resourceaccess.core..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "org.mybatis..", "..resourceaccess.persistence..", "..resourceaccess.api..",
                    "..resourceaccess.enforcement..", "..resourceaccess.bridge..", "..task..", "..a2a..", "..issuetracking..", "..agent..");

    @ArchTest static final ArchRule api_does_not_access_persistence_or_mappers = noClasses()
            .that().resideInAPackage("..resourceaccess.api..")
            .should().dependOnClassesThat().resideInAnyPackage("..resourceaccess.persistence..", "..mapper..", "org.mybatis..");

    @ArchTest static final ArchRule business_modules_do_not_depend_on_resource_access_persistence = noClasses()
            .that().resideInAnyPackage("..task..", "..a2a..", "..issuetracking..", "..agent..", "..execution..")
            .should().dependOnClassesThat().resideInAPackage("..resourceaccess.persistence..");
}
