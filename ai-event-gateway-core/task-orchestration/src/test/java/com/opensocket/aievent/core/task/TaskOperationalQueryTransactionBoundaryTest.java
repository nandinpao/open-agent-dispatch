package com.opensocket.aievent.core.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class TaskOperationalQueryTransactionBoundaryTest {

    @Test
    void tenantSensitiveOperationalReadsOpenReadOnlyTransactions() throws Exception {
        assertReadOnlyTransaction("findTask", String.class);
        assertReadOnlyTransaction("findTask", String.class, String.class);
        assertReadOnlyTransaction("findTaskFamily", String.class, String.class, int.class);
        assertReadOnlyTransaction("findTaskChildren", String.class, String.class, int.class);
    }

    private static void assertReadOnlyTransaction(String name, Class<?>... parameters) throws Exception {
        Method method = DefaultTaskOrchestrationFacade.class.getMethod(name, parameters);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional)
                .as("%s must define a Spring transaction boundary before tenant-sensitive Task mapper access", method)
                .isNotNull();
        assertThat(transactional.readOnly())
                .as("%s must be a read-only operational query transaction", method)
                .isTrue();
    }
}
