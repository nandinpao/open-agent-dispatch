package com.opensocket.aievent.core.capability;

/** Phase 6 semantic problem classification proposal/evidence. Taxonomy is descriptive only, never a routing destination. */
public record ProblemClassification(
        String classificationCode,
        String displayName,
        String semanticTaxonomy,
        double confidence,
        String rationale) {
    public ProblemClassification {
        if (confidence < 0.0d || confidence > 1.0d) throw new IllegalArgumentException("classification confidence must be between 0 and 1");
    }
}
