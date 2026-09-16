package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;

/** Storage bridge that exchanges an internal object reference for a short-lived opaque handle. */
public interface AttachmentContentGatewayPort {
    AttachmentContentHandle createHandle(
            ResourceAttachmentMetadata metadata,
            String authorizationDecisionId,
            String runtimeLeaseId,
            Instant leaseExpiresAt);
}
