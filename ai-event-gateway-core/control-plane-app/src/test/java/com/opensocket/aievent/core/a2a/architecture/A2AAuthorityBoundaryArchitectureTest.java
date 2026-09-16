package com.opensocket.aievent.core.a2a.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.opensocket.aievent")
class A2AAuthorityBoundaryArchitectureTest {

    @ArchTest
    static final ArchRule a2a_domain_and_contracts_do_not_depend_on_transport_provider_or_persistence =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.opensocket.aievent.core.a2a.core..",
                            "com.opensocket.aievent.core.a2a.application.port..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "io.netty..",
                            "org.mybatis..",
                            "org.springframework.web..",
                            "..database.persistence.a2a..",
                            "..integration.jira..",
                            "..integration.redmine..",
                            "..integration.gitlab..");

    @ArchTest
    static final ArchRule a2a_http_adapter_depends_on_ports_not_concrete_services_or_persistence =
            noClasses()
                    .that().resideInAPackage("com.opensocket.aievent.core.a2a.api..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..database.persistence.a2a..",
                            "org.mybatis..",
                            "..core.a2a.authority..",
                            "..integration.jira..",
                            "..integration.redmine..",
                            "..integration.gitlab..");

    @ArchTest
    static final ArchRule a2a_http_adapter_does_not_depend_on_concrete_application_services =
            noClasses()
                    .that().resideInAPackage("com.opensocket.aievent.core.a2a.api..")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Service");

    @ArchTest
    static final ArchRule a2a_persistence_does_not_depend_on_http_runtime_or_provider =
            noClasses()
                    .that().resideInAPackage("com.opensocket.aievent.database.persistence.a2a..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.web..",
                            "io.netty..",
                            "..integration.jira..",
                            "..integration.redmine..",
                            "..integration.gitlab..");

    @ArchTest
    static final ArchRule issue_tracking_does_not_depend_on_a2a_mutation =
            noClasses()
                    .that().resideInAnyPackage("..issue..", "..integration.issue..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.opensocket.aievent.core.a2a.application.port.in..",
                            "com.opensocket.aievent.core.task.domain..")
                    .because("Issue Tracking consumes domain events and must not invoke A2A or Task mutation authority");

    @ArchTest
    static final ArchRule issue_tracking_does_not_depend_on_result_acceptance_use_case =
            noClasses()
                    .that().resideInAnyPackage("..issue..", "..integration.issue..")
                    .should().dependOnClassesThat().haveSimpleName("A2AResultAcceptanceUseCase");

    @ArchTest
    static final ArchRule runtime_transport_does_not_depend_on_core_mutation =
            noClasses()
                    .that().resideInAnyPackage("com.opensocket.aievent.gateway.netty..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.opensocket.aievent.core.a2a.application.port.in..",
                            "com.opensocket.aievent.core.task.domain..")
                    .because("Runtime owns transport evidence, not A2A or Task business mutation");

    @ArchTest
    static final ArchRule a2a_application_services_do_not_depend_on_transport_persistence_or_provider =
            noClasses()
                    .that().resideInAPackage("com.opensocket.aievent.core.a2a.application.service..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.web..",
                            "org.mybatis..",
                            "io.netty..",
                            "..database.persistence..",
                            "..integration.jira..",
                            "..integration.redmine..",
                            "..integration.gitlab..");

    @ArchTest
    static final ArchRule a2a_outbound_ports_are_framework_and_adapter_neutral =
            noClasses()
                    .that().resideInAPackage("com.opensocket.aievent.core.a2a.application.port.out..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "org.mybatis..",
                            "io.netty..",
                            "..database.persistence..",
                            "..integration.jira..",
                            "..integration.redmine..",
                            "..integration.gitlab..");

    @ArchTest
    static final ArchRule compatibility_handlers_depend_on_inbound_ports_not_application_services =
            noClasses()
                    .that().resideOutsideOfPackage("com.opensocket.aievent.core.a2a.application..")
                    .should().dependOnClassesThat().resideInAPackage(
                            "com.opensocket.aievent.core.a2a.application.service..");

    @ArchTest
    static final ArchRule handoff_core_does_not_depend_on_issue_tracking_provider_or_projection =
            noClasses()
                    .that().haveSimpleName("HandoffContextService")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..integration.issue..",
                            "..integration.jira..",
                            "..integration.redmine..",
                            "..integration.gitlab..");

    @ArchTest
    static final ArchRule issue_relay_projection_does_not_depend_on_a2a_or_task_mutation_services =
            noClasses()
                    .that().haveSimpleName("IssueRelayService")
                    .should().dependOnClassesThat().haveSimpleName("A2AGovernanceService");
}
