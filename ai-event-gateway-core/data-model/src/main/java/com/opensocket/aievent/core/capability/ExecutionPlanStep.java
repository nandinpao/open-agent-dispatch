package com.opensocket.aievent.core.capability;

import java.util.List;
import java.util.Locale;

/**
 * Semantic Plan Step plus the side-effect safety contract that must be frozen before Plan Admission.
 * Provider/Agent/Pool/transport selection remains forbidden here.
 */
public record ExecutionPlanStep(
        String stepId,
        CapabilityRequirement requiredCapability,
        List<String> dependsOn,
        String purpose,
        boolean required,
        Integer sequenceHint,
        String sideEffect,
        String writeSemantics,
        String compensationBindingId,
        int maxBindingFallback) {
    public ExecutionPlanStep {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        sideEffect = normalizeSideEffect(sideEffect, requiredCapability);
        writeSemantics = normalizeWriteSemantics(writeSemantics, sideEffect);
        compensationBindingId = blank(compensationBindingId) ? null : compensationBindingId.trim();
        maxBindingFallback = Math.max(0, Math.min(maxBindingFallback, 20));
        if ("COMPENSATABLE".equals(writeSemantics) && compensationBindingId == null) {
            throw new IllegalArgumentException("COMPENSATABLE_WRITE_REQUIRES_COMPENSATION_BINDING");
        }
    }

    /** Backward-compatible constructor for pre-A0-R5 call sites and serialized plans. */
    public ExecutionPlanStep(String stepId,CapabilityRequirement requiredCapability,List<String> dependsOn,
            String purpose,boolean required,Integer sequenceHint) {
        this(stepId,requiredCapability,dependsOn,purpose,required,sequenceHint,null,null,null,0);
    }

    private static String normalizeSideEffect(String raw, CapabilityRequirement requirement) {
        if (!blank(raw)) {
            String v=raw.trim().toUpperCase(Locale.ROOT);
            if (!List.of("NONE","READ","WRITE").contains(v)) throw new IllegalArgumentException("UNSUPPORTED_SIDE_EFFECT:"+v);
            return v;
        }
        String op=requirement==null||blank(requirement.operation())?"":requirement.operation().trim().toUpperCase(Locale.ROOT);
        if (op.matches(".*(READ|QUERY|GET|LIST|SEARCH|STATUS|TRACE|ANALYZE|ANALYSIS|INSPECT|VALIDATE|CHECK|LOOKUP|FIND).*")) return "READ";
        if (op.matches(".*(NOOP|NONE).*")) return "NONE";
        // Unknown legacy operation is intentionally treated as WRITE rather than silently widened.
        return "WRITE";
    }

    private static String normalizeWriteSemantics(String raw,String sideEffect) {
        if (!"WRITE".equals(sideEffect)) return null;
        String v=blank(raw)?"NON_COMPENSATABLE":raw.trim().toUpperCase(Locale.ROOT);
        if (!List.of("IDEMPOTENT","COMPENSATABLE","NON_COMPENSATABLE").contains(v)) throw new IllegalArgumentException("UNSUPPORTED_WRITE_SEMANTICS:"+v);
        return v;
    }
    private static boolean blank(String v){return v==null||v.isBlank();}
}
