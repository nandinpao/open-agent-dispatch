package com.opensocket.aievent.core.kernel.persistence;

public record PersistenceWriteResult(
        String resourceId,
        PersistenceWriteOutcome outcome,
        int affectedRows) {

    public boolean applied() {
        return outcome == PersistenceWriteOutcome.APPLIED && affectedRows > 0;
    }

    public static PersistenceWriteResult applied(String resourceId, int affectedRows) {
        return new PersistenceWriteResult(resourceId, PersistenceWriteOutcome.APPLIED, affectedRows);
    }

    public static PersistenceWriteResult ownershipLost(String resourceId) {
        return new PersistenceWriteResult(resourceId, PersistenceWriteOutcome.OWNERSHIP_LOST, 0);
    }

    public static PersistenceWriteResult notFound(String resourceId) {
        return new PersistenceWriteResult(resourceId, PersistenceWriteOutcome.NOT_FOUND, 0);
    }

    public static PersistenceWriteResult conflict(String resourceId) {
        return new PersistenceWriteResult(resourceId, PersistenceWriteOutcome.CONFLICT, 0);
    }
}
