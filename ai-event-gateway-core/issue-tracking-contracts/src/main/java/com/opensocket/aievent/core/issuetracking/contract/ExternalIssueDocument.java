package com.opensocket.aievent.core.issuetracking.contract;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical provider-neutral document rendered only from approved OpenDispatch context.
 * Provider-specific field names, credentials, full Task payloads and transport metadata are excluded.
 */
public record ExternalIssueDocument(
        int schemaVersion,
        String projectionId,
        ExternalIssueTaskReference taskReference,
        String summary,
        String description,
        String issueType,
        String priority,
        List<String> labels,
        List<String> components,
        Map<String, Object> approvedContext,
        List<ExternalIssueComment> comments,
        List<ExternalIssueRelation> links,
        Map<String, String> sourceEvidence,
        long mappingVersion,
        String mappingSchemaHash) {

    public ExternalIssueDocument {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        projectionId = requireText(projectionId, "projectionId");
        taskReference = Objects.requireNonNull(taskReference, "taskReference is required");
        summary = requireText(summary, "summary");
        description = description == null ? "" : description;
        issueType = requireText(issueType, "issueType");
        priority = priority == null ? "" : priority.trim();
        labels = List.copyOf(labels == null ? List.of() : labels);
        components = List.copyOf(components == null ? List.of() : components);
        approvedContext = Map.copyOf(approvedContext == null ? Map.of() : approvedContext);
        comments = List.copyOf(comments == null ? List.of() : comments);
        links = List.copyOf(links == null ? List.of() : links);
        sourceEvidence = Map.copyOf(sourceEvidence == null ? Map.of() : sourceEvidence);
        if (mappingVersion < 1) throw new IllegalArgumentException("mappingVersion must be positive");
        mappingSchemaHash = requireText(mappingSchemaHash, "mappingSchemaHash");
    }

    /** Compatibility constructor for the Phase 3A canonical document. */
    public ExternalIssueDocument(int schemaVersion, String projectionId, String taskId,
            String summary, String description, String issueType, String priority,
            Map<String,Object> fields, List<String> labels,
            List<ExternalIssueRelation> relations, Map<String,String> sourceEvidence) {
        this(schemaVersion, projectionId, new ExternalIssueTaskReference(taskId,"","","") ,
                summary, description, issueType, priority, labels, List.of(), fields,
                List.of(), relations, sourceEvidence, 1, "legacy-unbound");
    }

    public String taskId() { return taskReference.taskId(); }
    public Map<String,Object> fields() { return approvedContext; }
    public List<ExternalIssueRelation> relations() { return links; }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }
}
