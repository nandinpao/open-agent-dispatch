package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.assignment.TaskAuthorizationProjectionPort;
import com.opensocket.aievent.core.resourceaccess.contract.DescriptorResolutionContext;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.resourceaccess.core.ResourceProjectionService;
import java.time.Instant;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** RS4 one-way Task domain -> Resource Access projection bridge. */
@Component
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public final class TaskAuthorizationProjectionAdapter implements TaskAuthorizationProjectionPort {
    private final ResourceProjectionService projection;
    public TaskAuthorizationProjectionAdapter(ResourceProjectionService projection) { this.projection = Objects.requireNonNull(projection); }
    @Override public void projectTask(String tenantId, String taskId, String correlationId) {
        if (tenantId == null || tenantId.isBlank() || taskId == null || taskId.isBlank()) return;
        Instant now = Instant.now();
        projection.project(new ResourceRef(tenantId, ResourceType.TASK, taskId),
                new DescriptorResolutionContext(correlationId == null || correlationId.isBlank() ? "rs4-task-assignment" : correlationId,
                        "rs4-task-assignment", now), "RS4_TASK_ASSIGNMENT");
    }
}
