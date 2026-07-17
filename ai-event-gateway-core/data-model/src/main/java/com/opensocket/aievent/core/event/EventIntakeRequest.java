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
     * Optional from Phase 32-C onward. Missing or blank values are normalized
     * to UNKNOWN so source-system-only intake can create a TRIAGE task.
     */
    private String eventType;
    private String errorCode;
    private String severity;
    private String message;
    private OffsetDateTime occurredAt;
    private Map<String, Object> attributes;
}
