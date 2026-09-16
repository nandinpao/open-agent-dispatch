package com.opensocket.aievent.core.resourceaccess.core;

import java.util.List;

public record GovernancePage<T>(List<T> items, String nextCursor, long totalCount) {
    public GovernancePage {
        items = items == null ? List.of() : List.copyOf(items);
        nextCursor = nextCursor == null ? "" : nextCursor;
        if (totalCount < 0) throw new IllegalArgumentException("totalCount must not be negative");
    }
}
