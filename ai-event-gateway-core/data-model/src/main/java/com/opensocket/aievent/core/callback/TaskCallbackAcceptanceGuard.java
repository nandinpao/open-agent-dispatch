package com.opensocket.aievent.core.callback;

/**
 * Extension point evaluated after the platform token/fencing checks and before
 * any callback mutates Task or Dispatch state.
 */
public interface TaskCallbackAcceptanceGuard {

    default int order() {
        return 1000;
    }

    TaskCallbackGuardDecision evaluate(TaskCallbackGuardContext context);
}
