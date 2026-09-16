package com.opensocket.aievent.core.uicapability.core;

public record UiOperationPrerequisites(boolean stepUpRequired, boolean approvalRequired) {
    public static final UiOperationPrerequisites NONE = new UiOperationPrerequisites(false, false);
}
