package com.opensocket.aievent.core.integration.issue.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.opensocket.aievent.core.events.TaskTerminalEvent;
import com.opensocket.aievent.core.integration.identity.IntegrationConnection;
import com.opensocket.aievent.core.integration.identity.IntegrationConnectionStatus;
import com.opensocket.aievent.core.integration.identity.IntegrationIdentityRepository;
import com.opensocket.aievent.core.integration.identity.IntegrationProjectMapping;
import com.opensocket.aievent.core.integration.identity.IntegrationProjectMappingVersion;
import com.opensocket.aievent.core.integration.identity.IntegrationProviderType;
import com.opensocket.aievent.core.integration.identity.ProjectMappingLifecycle;
import com.opensocket.aievent.core.integration.identity.ProjectMappingStatus;
import com.opensocket.aievent.core.integration.issue.automation.IssueAutomationActionCommand;
import com.opensocket.aievent.core.integration.issue.automation.IssueAutomationActionPort;
import com.opensocket.aievent.core.integration.issue.automation.IssueAutomationActionResult;
import com.opensocket.aievent.core.integration.issue.automation.IssueAutomationOperation;
import com.opensocket.aievent.core.issue.TaskIssueLink;
import com.opensocket.aievent.core.issue.TaskIssueLinkRepository;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.domain.TaskIssueSyncPolicy;
import com.opensocket.aievent.core.task.v53.TaskLifecycle;

class IssuePolicyExistingIssueSyncTest {
    private final String tenant = "tenant-hf12";
    private final String taskId = "task-hf12";
    private final String connectionId = "conn-redmine";
    private final String mappingId = "map-redmine";

    private InMemoryTaskRepository tasks;
    private IntegrationIdentityRepository identities;
    private IssuePolicyDecisionRepository decisions;
    private TaskIssueLinkRepository links;
    private IssueAutomationActionPort actions;
    private IssuePolicyOrchestrationService service;

    @BeforeEach
    void setup() {
        tasks = new InMemoryTaskRepository();
        identities = mock(IntegrationIdentityRepository.class);
        decisions = mock(IssuePolicyDecisionRepository.class);
        links = mock(TaskIssueLinkRepository.class);
        actions = mock(IssueAutomationActionPort.class);

        when(decisions.findByTaskAndPurpose(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(decisions.save(any(IssuePolicyDecision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(links.findAllByTenantAndTaskId(tenant, taskId)).thenReturn(List.of());
        when(identities.resolutionCandidates(any())).thenReturn(List.of(mapping()));
        when(identities.findConnection(tenant, connectionId)).thenReturn(Optional.of(connection()));
        when(identities.findMappingVersion(tenant, mappingId, 1)).thenReturn(Optional.of(mappingVersion()));
        when(actions.request(any())).thenAnswer(invocation -> {
            IssueAutomationActionCommand command = invocation.getArgument(0);
            return new IssueAutomationActionResult("act-hf12", command.idempotencyKey(), "PENDING", true);
        });

        service = new IssuePolicyOrchestrationService(tasks, identities, decisions, links, actions,
                new IssuePolicyOrchestrationProperties());
    }

    @Test
    void createsIssueWhenNoCanonicalTaskIssueLinkExists() {
        saveRequiredClosedTask();

        IssuePolicyDecision result = service.onTerminalEvent(event());

        ArgumentCaptor<IssueAutomationActionCommand> captor = ArgumentCaptor.forClass(IssueAutomationActionCommand.class);
        verify(actions).request(captor.capture());
        IssueAutomationActionCommand command = captor.getValue();
        assertThat(command.operation()).isEqualTo(IssueAutomationOperation.CREATE_ISSUE);
        assertThat(command.taskIssueLinkId()).isNull();
        assertThat(command.targetExternalIssueId()).isNull();
        assertThat(result.issueOperation()).isEqualTo("CREATE_ISSUE");
        assertThat(result.automationStatus()).isEqualTo(IssuePolicyAutomationStatus.ACTION_REQUESTED);
    }

    @Test
    void appendsCommentToConfirmedCanonicalIssueInsteadOfCreatingDuplicate() {
        saveRequiredClosedTask();
        when(links.findAllByTenantAndTaskId(tenant, taskId)).thenReturn(List.of(confirmedLink("link-1", "4711")));

        IssuePolicyDecision result = service.onTerminalEvent(event());

        ArgumentCaptor<IssueAutomationActionCommand> captor = ArgumentCaptor.forClass(IssueAutomationActionCommand.class);
        verify(actions).request(captor.capture());
        IssueAutomationActionCommand command = captor.getValue();
        assertThat(command.operation()).isEqualTo(IssueAutomationOperation.ADD_COMMENT);
        assertThat(command.taskIssueLinkId()).isEqualTo("link-1");
        assertThat(command.targetExternalIssueId()).isEqualTo("4711");
        assertThat(command.description()).contains("OpenDispatch Task " + taskId).contains("COMPLETED");
        assertThat(result.issueOperation()).isEqualTo("ADD_COMMENT");
    }

    @Test
    void refusesDuplicateCreateWhenPrimaryLinkIsStillPending() {
        saveRequiredClosedTask();
        TaskIssueLink pending = new TaskIssueLink();
        pending.setTenantId(tenant);
        pending.setLinkId("link-pending");
        pending.setTaskId(taskId);
        pending.setLinkRole("PRIMARY");
        pending.setConnectionId(connectionId);
        pending.setProjectMappingId(mappingId);
        pending.setSyncStatus(TaskIssueLink.SYNC_PENDING);
        pending.setIssueActionType("ISSUE_CREATE");
        when(links.findAllByTenantAndTaskId(tenant, taskId)).thenReturn(List.of(pending));

        IssuePolicyDecision result = service.onTerminalEvent(event());

        verify(actions, never()).request(any());
        assertThat(result.automationStatus()).isEqualTo(IssuePolicyAutomationStatus.FAILED);
        assertThat(result.lastErrorCode()).isEqualTo("ISSUE_EXISTING_LINK_NOT_CONFIRMED");
    }

    @Test
    void refusesExistingIssueWhoseBindingDiffersFromGovernedMapping() {
        saveRequiredClosedTask();
        TaskIssueLink link = confirmedLink("link-other", "4712");
        link.setConnectionId("conn-other");
        when(links.findAllByTenantAndTaskId(tenant, taskId)).thenReturn(List.of(link));

        IssuePolicyDecision result = service.onTerminalEvent(event());

        verify(actions, never()).request(any());
        assertThat(result.automationStatus()).isEqualTo(IssuePolicyAutomationStatus.FAILED);
        assertThat(result.lastErrorCode()).isEqualTo("ISSUE_EXISTING_LINK_BINDING_MISMATCH");
    }


    @Test
    void refusesUngovernedUpdateFallbackWhenExistingIssueCommentsAreDisabled() {
        saveRequiredClosedTask();
        when(links.findAllByTenantAndTaskId(tenant, taskId)).thenReturn(List.of(confirmedLink("link-disabled", "4713")));
        when(identities.resolutionCandidates(any())).thenReturn(List.of(mapping("DISABLED")));

        IssuePolicyDecision result = service.onTerminalEvent(event());

        verify(actions, never()).request(any());
        assertThat(result.automationStatus()).isEqualTo(IssuePolicyAutomationStatus.FAILED);
        assertThat(result.lastErrorCode()).isEqualTo("ISSUE_EXISTING_LINK_COMMENT_DISABLED");
    }

    private TaskRecord saveRequiredClosedTask() {
        TaskRecord task = new TaskRecord();
        task.setTenantId(tenant);
        task.setTaskId(taskId);
        task.setTaskKey("HF12-1");
        task.setStatus(TaskStatus.COMPLETED);
        task.setTaskLifecycle(TaskLifecycle.CLOSED);
        task.setIssueSyncPolicy(TaskIssueSyncPolicy.REQUIRED);
        task.setIssueSyncPolicySource("FLOW_DEFAULT");
        task.setSourceSystem("ERP");
        task.setFinalizationCompletedAt(OffsetDateTime.now().minusSeconds(1));
        task.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        task.setUpdatedAt(OffsetDateTime.now());
        return tasks.save(task);
    }

    private TaskIssueLink confirmedLink(String linkId, String externalIssueId) {
        TaskIssueLink link = new TaskIssueLink();
        link.setTenantId(tenant);
        link.setLinkId(linkId);
        link.setTaskId(taskId);
        link.setLinkRole("PRIMARY");
        link.setConnectionId(connectionId);
        link.setProjectMappingId(mappingId);
        link.setExternalIssueId(externalIssueId);
        link.setIssueId(externalIssueId);
        link.setExternalIssueUrl("https://redmine.example/issues/" + externalIssueId);
        link.setLinkState(TaskIssueLink.LINK_EXTERNAL_CONFIRMED);
        link.setSyncStatus(TaskIssueLink.SYNCED);
        link.setUpdatedAt(OffsetDateTime.now());
        return link;
    }

    private IntegrationConnection connection() {
        OffsetDateTime now = OffsetDateTime.now();
        return new IntegrationConnection(tenant, connectionId, IntegrationProviderType.REDMINE, "Redmine",
                "https://redmine.example", "ON_PREM", null, IntegrationConnectionStatus.ACTIVE,
                10000, null, null, null, true, 1L, now, now);
    }

    private IntegrationProjectMapping mapping() {
        return mapping("APPEND_ONLY");
    }

    private IntegrationProjectMapping mapping(String commentPolicy) {
        OffsetDateTime now = OffsetDateTime.now();
        return new IntegrationProjectMapping(tenant, mappingId, connectionId,
                null, null, null, null, null,
                "project-1", "ERP", "ISSUE", null,
                null, null, null, null, null, null,
                null, null, null,
                ProjectMappingStatus.VALID, 100, true, true, ProjectMappingLifecycle.ACTIVE, 1,
                "{{task.title}}", "{{task.description}}", List.of(), Map.of(), Map.of(),
                commentPolicy, "CANONICAL_ONLY", null, null, null, null,
                null, 1L, now, now);
    }

    private IntegrationProjectMappingVersion mappingVersion() {
        return new IntegrationProjectMappingVersion(tenant, mappingId, 1, ProjectMappingLifecycle.ACTIVE,
                "{}", "cfg-hash", "snapshot-1", "schema-hash", "hf12", OffsetDateTime.now());
    }

    private TaskTerminalEvent event() {
        return new TaskTerminalEvent(
                "evt-hf12", taskId, null, "source-hf12", "COMPLETED", "TASK", "NORMAL", tenant,
                null, null, null, null, "task.terminal", null, null, List.of(), null, null, null, null, null,
                "cb-hf12", "RESULT", "Task completed successfully.", "COMPLETED", null, null, Map.of(), OffsetDateTime.now(),
                "corr-hf12", "cause-hf12", "trace-hf12", null, "SYSTEM", "hf12-test");
    }
}
