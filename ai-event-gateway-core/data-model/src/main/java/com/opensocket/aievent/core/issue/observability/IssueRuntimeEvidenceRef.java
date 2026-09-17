package com.opensocket.aievent.core.issue.observability;

import java.time.OffsetDateTime;
import java.util.Map;

/** Safe evidence pointer. Secret material must never be stored in attributes. */
public record IssueRuntimeEvidenceRef(
        String evidenceType,
        String evidenceId,
        String authority,
        OffsetDateTime observedAt,
        Map<String, String> attributes) {}
