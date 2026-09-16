package com.opensocket.aievent.core.governance;

/**
 * Result of opening a mutation receipt. A replay never permits the mutation
 * handler to execute again.
 */
public record ApiMutationStart(ApiMutationReceipt receipt, boolean replay) {
    public boolean terminalReplay() {
        if (!replay || receipt == null || receipt.status() == null) return false;
        return switch (receipt.status()) {
            case "COMPLETED", "FAILED", "CONFLICT", "REPLAYED" -> true;
            default -> false;
        };
    }

    public boolean inProgressReplay() {
        return replay && !terminalReplay();
    }
}
