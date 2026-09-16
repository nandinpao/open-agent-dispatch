package com.opensocket.aievent.core.enforcement.activation.contract;

public interface AuthorityRouter {
    AuthorityDecision route(AuthorityRoutingContext context);
    long currentRevision();
    String currentChecksum();
}
