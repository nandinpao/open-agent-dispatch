package com.opensocket.aievent.core.iam.api.pagination;

import com.opensocket.aievent.core.iam.api.config.IamApiProperties;
import com.opensocket.aievent.core.iam.api.error.IamApiException;

/** Enforces the API-wide bounded pagination contract before a projection is invoked. */
public final class IamPaginationPolicy {
    private final int defaultSize;
    private final int maximumSize;

    public IamPaginationPolicy(IamApiProperties properties) {
        this.defaultSize = properties.defaultPageSize();
        this.maximumSize = properties.maximumPageSize();
    }

    public int page(int page) {
        if (page < 0) {
            throw IamApiException.badRequest("IAM_PAGE_INVALID", "page must be zero or greater");
        }
        return page;
    }

    public int size(int size) {
        int value = size <= 0 ? defaultSize : size;
        if (value > maximumSize) {
            throw IamApiException.badRequest(
                    "IAM_PAGE_SIZE_EXCEEDED",
                    "page size must not exceed " + maximumSize);
        }
        return value;
    }

    /**
     * Compatibility boundary for read-only directories that may still be called by an older Admin UI bundle.
     * The canonical client requests at most {@code maximumSize}; stale clients are safely bounded instead of
     * failing the whole administration workspace. Mutation and cursor contracts continue to use strict bounds.
     */
    public int sizeCapped(int size) {
        int value = size <= 0 ? defaultSize : size;
        return Math.min(value, maximumSize);
    }

    public int limit(int limit) {
        int value = limit <= 0 ? defaultSize : limit;
        if (value > maximumSize) {
            throw IamApiException.badRequest(
                    "IAM_PAGE_SIZE_EXCEEDED",
                    "cursor limit must not exceed " + maximumSize);
        }
        return value;
    }
}
