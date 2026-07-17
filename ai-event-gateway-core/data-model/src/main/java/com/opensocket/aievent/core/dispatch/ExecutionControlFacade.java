package com.opensocket.aievent.core.dispatch;

import java.util.List;

import com.opensocket.aievent.core.callback.DispatchRecoveryResult;
import com.opensocket.aievent.core.callback.TaskCallbackRequest;
import com.opensocket.aievent.core.callback.TaskCallbackResult;
import com.opensocket.aievent.core.callback.TaskCallbackType;

/** Public application boundary for dispatch execution, callback handling and recovery operations. */
public interface ExecutionControlFacade {
    DispatchExecutionResult executeDispatch(String dispatchRequestId);
    List<DispatchExecutionResult> executeDueDispatches(int limit);
    TaskCallbackResult handleCallback(TaskCallbackType type, String taskId, TaskCallbackRequest request);
    List<DispatchRecoveryResult> recoverTimedOutDispatches(int limit);
}
