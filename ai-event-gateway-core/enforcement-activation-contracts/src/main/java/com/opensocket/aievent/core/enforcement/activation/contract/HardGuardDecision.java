package com.opensocket.aievent.core.enforcement.activation.contract;

public record HardGuardDecision(boolean allowed, String reasonCode) {
    public HardGuardDecision {
        reasonCode = reasonCode == null || reasonCode.isBlank() ? (allowed ? "HARD_GUARD_ALLOW" : "HARD_GUARD_DENY") : reasonCode.trim();
    }
    public static HardGuardDecision allow(String reasonCode) { return new HardGuardDecision(true, reasonCode); }
    public static HardGuardDecision deny(String reasonCode) { return new HardGuardDecision(false, reasonCode); }
}
