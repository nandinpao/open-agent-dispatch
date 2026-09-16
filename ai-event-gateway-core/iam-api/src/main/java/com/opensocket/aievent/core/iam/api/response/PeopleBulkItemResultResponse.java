package com.opensocket.aievent.core.iam.api.response;

/** Per-Person result returned by a server-side bulk command. */
public record PeopleBulkItemResultResponse(
        String userId,
        String displayName,
        String outcome,
        int changedCount,
        String code,
        String message,
        String remediation) { }
