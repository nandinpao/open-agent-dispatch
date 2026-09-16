package com.opensocket.aievent.core.iam.persistence.exception;
public final class IamOptimisticLockException extends RuntimeException {
    public IamOptimisticLockException(String aggregate, String id, long expectedVersion) {
        super("IDENTITY_VERSION_CONFLICT: " + aggregate + " " + id + " expectedVersion=" + expectedVersion);
    }
}
