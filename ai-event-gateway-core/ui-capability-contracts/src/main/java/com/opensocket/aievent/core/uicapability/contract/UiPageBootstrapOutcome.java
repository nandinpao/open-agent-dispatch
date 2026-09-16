package com.opensocket.aievent.core.uicapability.contract;

/** Safe server-rendering result. Only PAGE permits rendering protected page content. */
public enum UiPageBootstrapOutcome {
    PAGE,
    STEP_UP_SHELL,
    REQUEST_ACCESS_SHELL,
    SAFE_RESOURCE_CHANGED_SHELL,
    ANTI_ENUMERATION_NOT_FOUND_SHELL
}
