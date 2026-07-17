package com.opensocket.aievent.core.assignment;

public record AssignmentFencingValidation(boolean accepted, String code, String reason) {
    public static AssignmentFencingValidation accepted(String reason) {
        return new AssignmentFencingValidation(true, "ACCEPTED", reason);
    }

    public static AssignmentFencingValidation rejected(String code, String reason) {
        return new AssignmentFencingValidation(false, code, reason);
    }
}
