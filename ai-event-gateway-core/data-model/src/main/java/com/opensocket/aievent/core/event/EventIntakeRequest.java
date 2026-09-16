package com.opensocket.aievent.core.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

/**
 * Event intake API request.
 *
 * <p>Keep this DTO JavaBean-style for Jackson/Spring MVC request binding:
 * no generated all-args constructor and no builder. I7.10.5 intentionally avoids
 * generated all-args constructor or builder here because Jackson 3 may prefer a
 * generated constructor for request deserialization, which makes sparse JSON
 * intake payloads more fragile. Optional fields should remain setter-bound.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class EventIntakeRequest {
    @NotBlank
    private String tenantId;
    @NotBlank
    private String sourceSystem;
    /** Optional explicit A0-R1 registration. Legacy callers may omit it and resolve the single default registration. */
    private String sourceRegistrationId;
    /** Stable source-side event identity. It may be used as the idempotency key when the registration requires SOURCE_EVENT_ID. */
    private String sourceEventId;
    /** Registration-scoped idempotency key. Same key + different payload is quarantined by Intake Authority. */
    private String idempotencyKey;
    /** Caller declaration only. It is evidence input and never an authorization authority. */
    private String assertedOriginPrincipal;
    /**
     * R3 envelope stage. Missing value defaults to EXTERNAL in normalization so legacy
     * callers of /api/events/intake remain compatible.
     */
    private String eventStage;
    /** Original external source when this is an Agent-generated A2A / RESULT / ISSUE event. */
    private String originSourceSystem;
    /** Intended collaborator / callback target system for A2A, RESULT, ISSUE, or CALLBACK events. */
    private String targetSystem;
    /** OpenClaw skill requested by the matched Flow-owned rule or by the producing Agent. */
    private String requestedSkill;
    /** CONSULT / SUBTASK / HANDOFF style mode for A2A intake2. */
    private String handoffMode;
    /** Correlates EXTERNAL, A2A, RESULT, ISSUE, and CALLBACK events in one chain. */
    private String correlationId;
    /** Parent task id when an Agent produces intake2 or RESULT. */
    private String parentTaskId;
    private String siteId;
    private String plantId;
    private String objectType;
    private String objectId;
    /**
     * Optional in the current SourceSystem-only intake contract. Missing or blank values are normalized
     * to UNKNOWN so source-system-only intake can create a TRIAGE task.
     */
    private String eventType;
    private String errorCode;
    private String severity;
    private String message;
    private OffsetDateTime occurredAt;
    private Map<String, Object> attributes;

    /** Server-attached authority evidence. Deliberately not a JavaBean getter/setter so JSON callers cannot supply it. */
    @lombok.Getter(lombok.AccessLevel.NONE)
    @lombok.Setter(lombok.AccessLevel.NONE)
    private transient com.opensocket.aievent.core.workload.WorkloadContext serverWorkloadContext;

    public com.opensocket.aievent.core.workload.WorkloadContext serverWorkloadContext() { return serverWorkloadContext; }
    public void attachServerWorkloadContext(com.opensocket.aievent.core.workload.WorkloadContext context) { this.serverWorkloadContext = context; }
}
