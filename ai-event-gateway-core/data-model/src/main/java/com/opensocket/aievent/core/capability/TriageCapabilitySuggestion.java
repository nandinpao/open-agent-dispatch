package com.opensocket.aievent.core.capability;

/** A Triage Agent may propose a canonical WHAT only. Provider, Agent, Pool and protocol selection are forbidden. */
public record TriageCapabilitySuggestion(
        String capabilityCode,
        String operation,
        double confidence,
        String rationale) {
    public TriageCapabilitySuggestion {
        if (confidence < 0.0d || confidence > 1.0d) throw new IllegalArgumentException("capability confidence must be between 0 and 1");
    }
}
