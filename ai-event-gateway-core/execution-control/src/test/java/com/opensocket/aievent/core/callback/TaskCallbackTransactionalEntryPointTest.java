package com.opensocket.aievent.core.callback;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class TaskCallbackTransactionalEntryPointTest {
    @Test
    void publicCallbackEntryPointsOwnTheSpringTransactionBoundary() throws Exception {
        for (String methodName : new String[]{"ack", "progress", "result", "error"}) {
            Method method = TaskCallbackService.class.getMethod(
                    methodName, String.class, TaskCallbackRequest.class);
            assertThat(method.getAnnotation(Transactional.class))
                    .as(methodName + " must start a transaction before self-delegating to handle()")
                    .isNotNull();
        }
    }
}
