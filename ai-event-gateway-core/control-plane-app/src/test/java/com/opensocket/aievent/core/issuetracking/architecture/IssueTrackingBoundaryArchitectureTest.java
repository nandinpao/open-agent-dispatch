package com.opensocket.aievent.core.issuetracking.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.opensocket.aievent")
class IssueTrackingBoundaryArchitectureTest {

    @ArchTest
    static final ArchRule issue_tracking_contracts_are_framework_provider_and_authority_neutral =
            noClasses()
                    .that().resideInAPackage("com.opensocket.aievent.core.issuetracking.contract..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "org.mybatis..",
                            "java.net.http..",
                            "com.opensocket.aievent.core.action.executor.issue..",
                            "com.opensocket.aievent.core.a2a.application..",
                            "com.opensocket.aievent.core.task.domain..");

    @ArchTest
    static final ArchRule issue_tracking_core_is_provider_transport_and_persistence_neutral =
            noClasses()
                    .that().resideInAPackage("com.opensocket.aievent.core.issuetracking.core..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "org.mybatis..",
                            "java.net.http..",
                            "..database.persistence..",
                            "..action.executor.issue..",
                            "..integration.jira..",
                            "..integration.redmine..",
                            "..integration.gitlab..",
                            "..a2a.application..",
                            "..task.domain..");

    @ArchTest
    static final ArchRule provider_adapters_do_not_depend_on_task_or_a2a_mutation_authority =
            noClasses()
                    .that().resideInAPackage("com.opensocket.aievent.core.action.executor.issue..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.opensocket.aievent.core.a2a.application.port.in..",
                            "com.opensocket.aievent.core.a2a.application.service..",
                            "com.opensocket.aievent.core.task.domain..");

    @ArchTest
    static final ArchRule webhook_ingress_does_not_depend_on_task_or_a2a_mutation_authority =
            noClasses()
                    .that().resideInAPackage("com.opensocket.aievent.core.integration.issue.webhook..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.opensocket.aievent.core.a2a.application..",
                            "com.opensocket.aievent.core.task.domain..");

    @ArchTest
    static final ArchRule a2a_and_handoff_do_not_depend_on_issue_tracking_boundary =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.opensocket.aievent.core.a2a..",
                            "com.opensocket.aievent.core.integration.handoff..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.opensocket.aievent.core.issuetracking..",
                            "com.opensocket.aievent.core.integration.issue..");
}
