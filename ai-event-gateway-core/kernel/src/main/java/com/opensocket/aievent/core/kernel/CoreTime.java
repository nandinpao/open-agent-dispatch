package com.opensocket.aievent.core.kernel;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Framework-neutral UTC time helper for contracts and audit timestamps. */
public final class CoreTime {
    private CoreTime() {
    }

    public static OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
