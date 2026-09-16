package com.opensocket.aievent.core.integration.handoff;

/** Provider-neutral acknowledgement returned by the Dispatch Authority bridge. */
public record HandoffDispatchReleaseResult(
        boolean accepted,
        String evidenceReference,
        boolean replayed) {

    public static HandoffDispatchReleaseResult notApplicable(String evidenceReference) {
        return new HandoffDispatchReleaseResult(true, evidenceReference, true);
    }
}
