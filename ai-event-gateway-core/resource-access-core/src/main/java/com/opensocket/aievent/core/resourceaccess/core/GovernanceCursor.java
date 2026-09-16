package com.opensocket.aievent.core.resourceaccess.core;

import java.time.Instant;

/** Stable seek cursor. It is encoded by the service before crossing the API boundary. */
public record GovernanceCursor(Instant sortTime, String sortId) {
    public GovernanceCursor {
        sortTime = sortTime == null ? Instant.parse("9999-12-31T23:59:59Z") : sortTime;
        sortId = sortId == null ? "" : sortId.trim();
    }
    public static GovernanceCursor first() { return new GovernanceCursor(Instant.parse("9999-12-31T23:59:59Z"), ""); }
}
