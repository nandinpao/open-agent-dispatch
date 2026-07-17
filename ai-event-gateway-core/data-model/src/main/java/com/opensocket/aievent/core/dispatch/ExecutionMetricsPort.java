package com.opensocket.aievent.core.dispatch;

import java.time.Duration;

import com.opensocket.aievent.core.callback.TaskCallbackResult;

/** Optional observability port. The execution-control module does not depend on the observability implementation. */
public interface ExecutionMetricsPort {
    void recordDispatchExecution(DispatchExecutionResult result, Duration elapsed);
    void recordCallback(TaskCallbackResult result);

    static ExecutionMetricsPort noop() {
        return NoopExecutionMetricsPort.INSTANCE;
    }

    final class NoopExecutionMetricsPort implements ExecutionMetricsPort {
        private static final NoopExecutionMetricsPort INSTANCE = new NoopExecutionMetricsPort();
        private NoopExecutionMetricsPort() {
        }
        @Override
        public void recordDispatchExecution(DispatchExecutionResult result, Duration elapsed) {
        }
        @Override
        public void recordCallback(TaskCallbackResult result) {
        }
    }
}
