package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** A0-R6 fallback classification. It never widens an R5 envelope by itself. */
public record RebindingAdmissionDecision(
        String decisionId,String tenantId,String planId,int planRevision,String stepId,String envelopeId,
        String requestedBindingId,String requestedBindingClass,String sideEffect,String writeSemantics,
        String result,List<String> reasonCodes,OffsetDateTime evaluatedAt) {
    public RebindingAdmissionDecision { reasonCodes=reasonCodes==null?List.of():List.copyOf(reasonCodes); }
}
