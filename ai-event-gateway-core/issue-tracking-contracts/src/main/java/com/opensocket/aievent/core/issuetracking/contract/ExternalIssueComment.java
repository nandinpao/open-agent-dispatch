package com.opensocket.aievent.core.issuetracking.contract;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Canonical approved comment, independent of provider comment models. */
public record ExternalIssueComment(
        String commentId,
        String body,
        String authorReference,
        OffsetDateTime occurredAt) {
    public ExternalIssueComment {
        commentId = requireText(commentId,"commentId");
        body = requireText(body,"body");
        authorReference = authorReference==null?"":authorReference.trim();
        occurredAt = Objects.requireNonNull(occurredAt,"occurredAt is required");
    }
    private static String requireText(String value,String name){Objects.requireNonNull(value,name+" is required");String v=value.trim();if(v.isEmpty())throw new IllegalArgumentException(name+" is required");return v;}
}
