package com.opensocket.aievent.core.task;

/**
 * Canonical Task context used by every external-Issue binding/execution path.
 *
 * <p>The Issue policy gate and the provider executor must resolve the same Project Mapping.
 * Executor ownership is authoritative after dispatch; owner/origin/requester values are only
 * fallbacks for Tasks that have not populated executor scope.</p>
 */
public record TaskIssueBindingContext(
        String tenantId,
        String departmentId,
        String groupId,
        String serviceDomainId,
        String sourceSystemId,
        String taskType) {

    public static TaskIssueBindingContext from(TaskRecord task) {
        if (task == null) throw new IllegalArgumentException("task is required");
        String executorDomain = clean(task.getExecutorDomainId());
        String requesterDomain = clean(task.getRequesterDomainId());
        boolean a2a = clean(task.getParentTaskId()) != null || "A2A".equalsIgnoreCase(clean(task.getEventStage()));
        String sourceSystem = a2a && executorDomain != null
                ? executorDomain
                : first(task.getSourceSystem(), task.getOriginSourceSystem(), task.getOriginWorkloadSourceSystem(), executorDomain);
        return new TaskIssueBindingContext(
                required(task.getTenantId(), "tenantId"),
                first(task.getExecutorDepartmentId(), task.getOwnerDepartmentId(), task.getOriginDepartmentId()),
                first(task.getExecutorGroupId(), task.getOwnerGroupId(), task.getOriginGroupId()),
                first(executorDomain, requesterDomain),
                sourceSystem,
                clean(task.getEffectiveTaskTypeCode()));
    }

    private static String first(String... values) {
        if (values == null) return null;
        for (String value : values) {
            String cleaned = clean(value);
            if (cleaned != null) return cleaned;
        }
        return null;
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.trim();
        return "UNASSIGNED".equalsIgnoreCase(cleaned) ? null : cleaned;
    }

    private static String required(String value, String field) {
        String cleaned = clean(value);
        if (cleaned == null) throw new IllegalArgumentException(field + " is required");
        return cleaned;
    }
}
