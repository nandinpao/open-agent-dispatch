package com.opensocket.aievent.core.issuetracking.contract;

import java.util.Objects;

/** Minimum Task reference allowed to cross the provider boundary. */
public record ExternalIssueTaskReference(
        String taskId,
        String taskType,
        String sourceSystem,
        String correlationId) {
    public ExternalIssueTaskReference {
        taskId = requireText(taskId, "taskId");
        taskType = normalize(taskType);
        sourceSystem = normalize(sourceSystem);
        correlationId = normalize(correlationId);
    }
    private static String requireText(String value,String name){Objects.requireNonNull(value,name+" is required");String v=value.trim();if(v.isEmpty())throw new IllegalArgumentException(name+" is required");return v;}
    private static String normalize(String value){return value==null?"":value.trim();}
}
