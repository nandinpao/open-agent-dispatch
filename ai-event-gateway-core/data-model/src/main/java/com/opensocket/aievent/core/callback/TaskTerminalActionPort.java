package com.opensocket.aievent.core.callback;

import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.task.TaskRecord;

/** Outbound port invoked after a terminal callback; implemented by the adapter-action module/application. */
public interface TaskTerminalActionPort {
    void onTerminalTaskCallback(TaskRecord task,
                                DispatchRequest dispatchRequest,
                                TaskCallbackRequest callback,
                                TaskCallbackType callbackType);

    static TaskTerminalActionPort noop() {
        return (task, dispatchRequest, callback, callbackType) -> { };
    }
}
