package com.opensocket.aievent.core.capability;

/** Child Task identity. Runtime execution requires authoritative=true. */
public record PlanChildTaskReference(String taskId,boolean authoritative) {}
