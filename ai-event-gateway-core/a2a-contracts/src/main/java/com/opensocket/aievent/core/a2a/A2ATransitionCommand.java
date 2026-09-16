package com.opensocket.aievent.core.a2a;

public enum A2ATransitionCommand {
    START_VALIDATION,
    REQUIRE_APPROVAL,
    APPROVE,
    REJECT,
    BEGIN_CHILD_CREATION,
    CREATE_CHILD,
    REQUEST_DISPATCH,
    START_DISPATCH,
    MARK_BLOCKED,
    RECOVER,
    MARK_RUNNING,
    WAIT_FOR_RESULT,
    COMPLETE,
    FAIL,
    CANCEL,
    CONFIRM_CANCEL,
    EXPIRE
}
