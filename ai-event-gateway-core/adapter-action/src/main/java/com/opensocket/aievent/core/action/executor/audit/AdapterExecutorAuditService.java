package com.opensocket.aievent.core.action.executor.audit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterExecutionResult;
import com.opensocket.aievent.core.action.executor.AdapterSecretRedactor;

@Service
public class AdapterExecutorAuditService {
    private final AdapterExecutorAuditRepository repository;
    private final AdapterActionExecutionProperties properties;

    public AdapterExecutorAuditService(AdapterExecutorAuditRepository repository, AdapterActionExecutionProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public void record(AdapterAction action,
                       AdapterActionStatus beforeStatus,
                       AdapterActionStatus afterStatus,
                       AdapterExecutionResult result,
                       String message) {
        if (action == null) return;
        AdapterExecutorAuditRecord r = new AdapterExecutorAuditRecord();
        r.setAuditId("adapter-audit-" + UUID.randomUUID());
        r.setActionId(action.getActionId());
        r.setTaskId(action.getTaskId());
        r.setIncidentId(action.getIncidentId());
        r.setAdapterType(action.getAdapterType() == null ? null : action.getAdapterType().name());
        r.setActionType(action.getActionType() == null ? null : action.getActionType().name());
        r.setExecutorName(action.getExecutorName());
        r.setBeforeStatus(beforeStatus == null ? null : beforeStatus.name());
        r.setAfterStatus(afterStatus == null ? null : afterStatus.name());
        r.setOutcome(result == null || result.getOutcome() == null ? null : result.getOutcome().name());
        r.setMessage(AdapterSecretRedactor.redactText(message == null && result != null ? result.getError() : message));
        r.setAttemptCount(action.getAttemptCount());
        r.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (properties.getAudit().isPayloadSnapshotEnabled()) {
            r.setPayloadSnapshot(AdapterSecretRedactor.redactMap(new LinkedHashMap<>(action.getPayload() == null ? Map.of() : action.getPayload())));
        }
        repository.save(r);
    }
}
