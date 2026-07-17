package com.opensocket.aievent.core.kernel.persistence;

public final class PersistenceWriteVerifier {
    private PersistenceWriteVerifier() {
    }

    public static PersistenceWriteResult requireApplied(
            PersistenceWriteResult result,
            String operation) {
        if (result == null || !result.applied()) {
            String detail = result == null ? "null result" : result.outcome().name();
            throw new IllegalStateException(operation + " was not applied: " + detail);
        }
        return result;
    }
}
