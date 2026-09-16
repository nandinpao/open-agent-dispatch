package com.opensocket.aievent.core.integration.issue.webhook;
public record CanonicalExternalObservation(String rawJson,String canonicalJson,String canonicalHash,String profileVersion) {}
