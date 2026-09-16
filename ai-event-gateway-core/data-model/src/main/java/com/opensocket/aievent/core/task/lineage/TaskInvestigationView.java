package com.opensocket.aievent.core.task.lineage;

import java.util.List;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.domain.TaskChainView;

/** Read model for investigator drill-down. Authorization is enforced by the API/Resource Access layer. */
public record TaskInvestigationView(
        String tenantId,
        String requestedTaskId,
        TaskRecord task,
        TaskChainView chain,
        List<TaskLineageEvidence> lineage) {}
