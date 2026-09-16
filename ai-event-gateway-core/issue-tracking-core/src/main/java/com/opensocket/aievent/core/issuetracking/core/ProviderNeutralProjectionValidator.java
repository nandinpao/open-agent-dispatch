package com.opensocket.aievent.core.issuetracking.core;

import com.opensocket.aievent.core.issuetracking.contract.ExternalIssueDocument;
import com.opensocket.aievent.core.issuetracking.contract.IssueProjectionIntent;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/** Enforces the canonical provider boundary and recursively blocks secret-like content. */
public final class ProviderNeutralProjectionValidator {
    private static final String[] FORBIDDEN_FIELD_FRAGMENTS = {
            "password", "secret", "credential", "authorization", "api_key", "apikey",
            "access_token", "refresh_token", "dispatch_token", "fencing_token", "private_key",
            "session_cookie", "raw_payload", "original_payload", "source_payload"
    };

    public IssueProjectionValidation validate(IssueProjectionIntent intent) {
        var violations = new ArrayList<String>();
        if (intent == null) return IssueProjectionValidation.rejected(java.util.List.of("intent is required"));
        ExternalIssueDocument document = intent.document();
        if (!intent.projectionId().equals(document.projectionId())) violations.add("projectionId must match the canonical document");
        if (!intent.taskId().equals(document.taskId())) violations.add("taskId must match the canonical document");
        if (!intent.aggregateKey().stableProjectionId().equals(intent.projectionId())) violations.add("projectionId must be derived from the aggregate key");
        if (!intent.aggregateKey().tenantId().equals(intent.tenantId())) violations.add("aggregate tenantId must match intent tenantId");
        if (!intent.aggregateKey().taskId().equals(intent.taskId())) violations.add("aggregate taskId must match intent taskId");
        if (!intent.aggregateKey().connectionId().equals(intent.connectionId())) violations.add("aggregate connectionId must match intent connectionId");
        if (!intent.aggregateKey().projectMappingId().equals(intent.projectMappingId())) violations.add("aggregate projectMappingId must match intent projectMappingId");
        if (intent.projectionPurpose() != intent.aggregateKey().projectionPurpose()) violations.add("projectionPurpose must match aggregate key");
        if (intent.mappingVersion() != document.mappingVersion()) violations.add("mappingVersion must match canonical document");
        if (!intent.mappingSchemaHash().equals(document.mappingSchemaHash())) violations.add("mappingSchemaHash must match canonical document");
        validateValue(document.approvedContext(), "approvedContext", violations);
        validateValue(document.sourceEvidence(), "sourceEvidence", violations);
        validateValue(document.comments(), "comments", violations);
        return violations.isEmpty() ? IssueProjectionValidation.accepted() : IssueProjectionValidation.rejected(violations);
    }

    private static void validateValue(Object value, String path, java.util.List<String> violations) {
        if (value == null) return;
        if (value instanceof Map<?,?> map) {
            for (var entry : map.entrySet()) {
                String key=String.valueOf(entry.getKey());
                validateKey(key,path,violations);
                validateValue(entry.getValue(),path+"."+key,violations);
            }
            return;
        }
        if (value instanceof Collection<?> values) {
            int index=0; for (Object item:values) validateValue(item,path+"["+(index++)+"]",violations); return;
        }
        if (value.getClass().isArray()) {
            for(int i=0;i<Array.getLength(value);i++) validateValue(Array.get(value,i),path+"["+i+"]",violations);
        }
    }

    private static void validateKey(String key,String path,java.util.List<String> violations) {
        String normalized=key.toLowerCase(Locale.ROOT).replace('-','_').replace(' ','_');
        for(String forbidden:FORBIDDEN_FIELD_FRAGMENTS) if(normalized.contains(forbidden)) {
            violations.add(path+" contains forbidden sensitive field: "+key); return;
        }
    }
}
