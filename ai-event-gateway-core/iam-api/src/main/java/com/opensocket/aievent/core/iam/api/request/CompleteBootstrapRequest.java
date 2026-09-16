package com.opensocket.aievent.core.iam.api.request;

/** Recovery-code acknowledgement is enforced during MFA confirmation. */
public record CompleteBootstrapRequest(long expectedVersion) {}