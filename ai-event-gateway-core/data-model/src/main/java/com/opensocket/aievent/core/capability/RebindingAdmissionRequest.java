package com.opensocket.aievent.core.capability;

/** Requested fallback Binding. The service classifies whether R5 authority already covers it. */
public record RebindingAdmissionRequest(Integer planRevision,String stepId,String requestedBindingId) {}
