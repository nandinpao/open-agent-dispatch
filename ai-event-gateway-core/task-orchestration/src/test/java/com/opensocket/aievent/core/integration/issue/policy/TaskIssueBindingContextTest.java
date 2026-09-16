package com.opensocket.aievent.core.integration.issue.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.task.TaskIssueBindingContext;
import com.opensocket.aievent.core.task.TaskRecord;

class TaskIssueBindingContextTest {
    @Test
    void executorScopeWinsAndUnassignedDoesNotPoisonMappingContext() {
        TaskRecord task = new TaskRecord();
        task.setTenantId("tenant-a");
        task.setSourceSystem("ERP");
        task.setOriginSourceSystem("EDGE");
        task.setOwnerDepartmentId("dept-owner");
        task.setOwnerGroupId("group-owner");
        task.setRequesterDomainId("UNASSIGNED");
        task.setExecutorDepartmentId("dept-exec");
        task.setExecutorGroupId("group-exec");
        task.setExecutorDomainId("ERP-DOMAIN");
        task.setTaskTypeCode("INCIDENT_RESPONSE");

        TaskIssueBindingContext context = TaskIssueBindingContext.from(task);

        assertThat(context.departmentId()).isEqualTo("dept-exec");
        assertThat(context.groupId()).isEqualTo("group-exec");
        assertThat(context.serviceDomainId()).isEqualTo("ERP-DOMAIN");
        assertThat(context.sourceSystemId()).isEqualTo("ERP");
        assertThat(context.taskType()).isEqualTo("INCIDENT_RESPONSE");
    }

    @Test
    void a2aChildUsesExecutorDomainAsCanonicalSource() {
        TaskRecord task = new TaskRecord();
        task.setTenantId("tenant-a");
        task.setSourceSystem("ERP");
        task.setEventStage("A2A");
        task.setParentTaskId("parent-1");
        task.setExecutorDomainId("MES");

        TaskIssueBindingContext context = TaskIssueBindingContext.from(task);

        assertThat(context.serviceDomainId()).isEqualTo("MES");
        assertThat(context.sourceSystemId()).isEqualTo("MES");
    }
}
